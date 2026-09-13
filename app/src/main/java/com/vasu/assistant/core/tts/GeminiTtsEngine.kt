package com.vasu.assistant.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.vasu.assistant.core.ai.LanguageDetector
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GeminiTtsEngine - High-fidelity online Hindi female assistant voice using Google Gemini TTS.
 *
 * VOICE PIPELINE (single owner for Gemini-mode assistant speech):
 *   complete AI response -> sanitizeForSpeech (toSpeakableText) ->
 *   Gemini native TTS (prebuilt female voice) -> Base64 decode ->
 *   raw 16-bit mono PCM (24 kHz where returned) ->
 *   AudioTrack MODE_STREAM -> phone speaker / Bluetooth.
 *
 * - Uses actual Gemini speech generation models:
 *   Primary: gemini-3.1-flash-tts-preview
 *   Fallback 1: gemini-2.5-flash-preview-tts
 *   Fallback 2: gemini-2.0-flash
 * - Prebuilt natural female assistant voice: "Kore" (warm, conversational, friendly).
 * - Raw PCM is streamed through ONE AudioTrack (MODE_STREAM, 24 kHz mono
 *   16-bit where the API returns that format). Non-PCM payloads (WAV/MP3)
 *   keep the MediaPlayer path. MediaPlayer is NEVER used for raw PCM.
 * - Completion is event-driven (track drained / onCompletion), never a
 *   fixed delay. TTS_COMPLETED fires only after the full PCM played.
 * - ONE assistant response = ONE Gemini TTS request = ONE playback.
 * - Failures are reported via TTS_ERROR logs + onError (controlled failure
 *   state). This engine NEVER silently substitutes a local voice; routing
 *   policy lives in VoiceRouter. API keys are never logged.
 */
@Singleton
class GeminiTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: SecureKeyStore,
    private val settings: VasuSettings
) : VoiceEngine {

    override val engineName: String = "GeminiTtsEngine"

    override val isAvailable: Boolean
        get() = isOnline() && keyStore.hasGeminiKey() && !settings.offlineOnly.value

    /** Centralized TTS lifecycle: exactly one state at a time. */
    private val _ttsState = MutableStateFlow(GeminiTtsState.IDLE)
    val ttsState: StateFlow<GeminiTtsState> = _ttsState.asStateFlow()

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // In-memory LRU cache for synthesized audio (up to 25 recent phrases).
    // Keyed by voice + speakable text; stores raw bytes with their MIME/rate
    // so PCM stays PCM (AudioTrack) and WAV/MP3 stays containerized.
    private val audioMemoryCache = LruCache<String, CachedGeminiAudio>(25)

    // ONE centralized audio owner: either a MediaPlayer (WAV/MP3) or an
    // AudioTrack (raw PCM) is active at any moment, never both, never two.
    private val audioOwnerLock = Any()
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override suspend fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        val speakable = toSpeakableText(text)
        if (speakable.isBlank()) {
            _ttsState.value = GeminiTtsState.COMPLETED
            withContext(Dispatchers.Main) { onDone?.invoke() }
            return@withContext true
        }

        val ttsId = "tts_${System.currentTimeMillis()}_${speakable.hashCode()}"
        val voiceName = settings.geminiTtsVoice.value.ifBlank { VasuSettings.DEFAULT_GEMINI_TTS_VOICE }
        val ttsLang = LanguageDetector.detect(speakable).style.name
        _ttsState.value = GeminiTtsState.GENERATING

        // 1. Connectivity check
        if (!isOnline() || settings.offlineOnly.value) {
            val err = "Gemini network unavailable"
            Log.w(TAG, err)
            ttsError(provider = "gemini", model = "-", voice = voiceName, category = err)
            _ttsState.value = GeminiTtsState.ERROR
            withContext(Dispatchers.Main) { onError?.invoke(err) }
            return@withContext false
        }

        // 2. API key check (presence only — the key itself is never logged)
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            val err = "Gemini API key missing"
            Log.w(TAG, err)
            ttsError(provider = "gemini", model = "-", voice = voiceName, category = err)
            _ttsState.value = GeminiTtsState.ERROR
            withContext(Dispatchers.Main) { onError?.invoke(err) }
            return@withContext false
        }

        // 3. Cache lookup (raw PCM stays PCM for the AudioTrack path)
        val cacheKey = "${voiceName}_$speakable"
        val cachedAudio = audioMemoryCache.get(cacheKey)
        if (cachedAudio != null) {
            Log.d(TAG, "GEMINI_TTS_REQUEST ttsId=$ttsId cached=true model=cached voice=$voiceName language=$ttsLang")
            Log.d(TAG, "AUDIO_RECEIVED ttsId=$ttsId mime=${cachedAudio.mimeType} bytes=${cachedAudio.bytes.size}")
            return@withContext playAudioBytes(cachedAudio, ttsId, onStart, onDone, onError)
        }

        // 4. Synthesize via Gemini API with model fallback chain
        val preferredModel = settings.geminiTtsModel.value
        val modelChain = linkedSetOf(
            preferredModel,
            VasuSettings.DEFAULT_GEMINI_TTS_MODEL,
            VasuSettings.FALLBACK_GEMINI_TTS_MODEL,
            VasuSettings.BASE_GEMINI_TTS_MODEL
        ).toList()

        var lastErrorReason = "Gemini TTS synthesis failed"

        for (model in modelChain) {
            Log.i(TAG, "GEMINI_TTS_REQUEST ttsId=$ttsId model=$model voice=$voiceName language=$ttsLang")
            Log.i(TAG, "GeminiTTS model=$model")
            Log.i(TAG, "GeminiTTS voice=$voiceName")
            Log.i(TAG, "GeminiTTS language=$ttsLang")
            val synthesisResult = requestGeminiAudio(model, apiKey, speakable)
            when (synthesisResult) {
                is TtsSynthesisResult.Success -> {
                    audioMemoryCache.put(cacheKey, CachedGeminiAudio(synthesisResult.audioBytes, synthesisResult.mimeType, synthesisResult.sampleRate))
                    Log.d(TAG, "AUDIO_RECEIVED ttsId=$ttsId mime=${synthesisResult.mimeType} bytes=${synthesisResult.audioBytes.size}")
                    return@withContext playAudioBytes(
                        CachedGeminiAudio(synthesisResult.audioBytes, synthesisResult.mimeType, synthesisResult.sampleRate),
                        ttsId, onStart, onDone, onError
                    )
                }
                is TtsSynthesisResult.ModelNotFound -> {
                    Log.w(TAG, "Model $model not found or unsupported for TTS on this key, trying next fallback")
                    ttsError(provider = "gemini", model = model, voice = voiceName, category = "model_not_found")
                    lastErrorReason = "Gemini TTS model unavailable"
                }
                is TtsSynthesisResult.Failure -> {
                    lastErrorReason = synthesisResult.reason
                    ttsError(provider = "gemini", model = model, voice = voiceName, category = synthesisResult.reason)
                    // Non-model errors (key rejected, quota exhausted, network dropped) should not cycle through all models
                    if (synthesisResult.shouldHaltChain) {
                        break
                    }
                }
            }
        }

        Log.e(TAG, "Gemini TTS failed across all candidates: $lastErrorReason")
        _ttsState.value = GeminiTtsState.ERROR
        withContext(Dispatchers.Main) { onError?.invoke(lastErrorReason) }
        return@withContext false
    }

    private fun requestGeminiAudio(model: String, apiKey: String, text: String): TtsSynthesisResult {
        val voiceName = settings.geminiTtsVoice.value.ifBlank { VasuSettings.DEFAULT_GEMINI_TTS_VOICE }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val systemInstruction = "Speak naturally in Hindi as a young, friendly female personal AI assistant. " +
            "Use clear Indian Hindi pronunciation. Keep the delivery concise, warm, confident and conversational. " +
            "Do not sound like a news reader or robotic TTS."

        val promptText = "$systemInstruction\n\nText to speak:\n$text"

        val payload = try {
            JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
            }.toString()
        } catch (e: Exception) {
            return TtsSynthesisResult.Failure("Gemini audio request payload creation failed: ${e.message}", true)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    parseAudioResponse(body)
                } else {
                    mapHttpError(response.code, body, model)
                }
            }
        } catch (e: UnknownHostException) {
            TtsSynthesisResult.Failure("Gemini network unavailable", true)
        } catch (e: SocketTimeoutException) {
            TtsSynthesisResult.Failure("Gemini request timed out", false)
        } catch (e: IOException) {
            TtsSynthesisResult.Failure("Gemini network unavailable: ${e.message}", true)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling Gemini TTS", e)
            TtsSynthesisResult.Failure("Gemini audio decoding failure: ${e.message}", false)
        }
    }

    private fun mapHttpError(code: Int, body: String, model: String): TtsSynthesisResult {
        val errorMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")

        return when (code) {
            400, 401 -> {
                Log.e(TAG, "Gemini authentication error ($code): $errorMessage")
                TtsSynthesisResult.Failure("Gemini API key invalid", true)
            }
            403 -> {
                if (errorMessage.contains("quota", ignoreCase = true)) {
                    TtsSynthesisResult.Failure("Gemini quota/rate-limit", true)
                } else {
                    TtsSynthesisResult.Failure("Gemini permission denied", true)
                }
            }
            404 -> TtsSynthesisResult.ModelNotFound(model)
            429 -> TtsSynthesisResult.Failure("Gemini quota/rate-limit", true)
            in 500..599 -> TtsSynthesisResult.Failure("Gemini server error (HTTP $code)", false)
            else -> TtsSynthesisResult.Failure("Gemini TTS HTTP error ($code): $errorMessage", false)
        }
    }

    private fun parseAudioResponse(body: String): TtsSynthesisResult {
        try {
            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return TtsSynthesisResult.Failure("Gemini audio decoding failure: No candidates returned", false)
            }

            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")

            if (parts == null || parts.length() == 0) {
                return TtsSynthesisResult.Failure("Gemini audio decoding failure: Missing parts in candidate", false)
            }

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inlineData = part.optJSONObject("inlineData") ?: continue
                val mimeType = inlineData.optString("mimeType", "")
                val base64Data = inlineData.optString("data", "")

                if (base64Data.isNotBlank()) {
                    val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    // Verify the actual MIME before deciding the playback path.
                    // Raw PCM (audio/pcm, optionally audio/l16 with rate=...) goes
                    // to AudioTrack; containers (WAV/MP3) keep MediaPlayer.
                    // Base64 text is NEVER fed into AudioTrack.
                    return if (mimeType.startsWith("audio/pcm") || mimeType.startsWith("audio/l16")) {
                        val sampleRate = extractSampleRate(mimeType)
                        TtsSynthesisResult.Success(rawBytes, mimeType, sampleRate)
                    } else {
                        TtsSynthesisResult.Success(rawBytes, mimeType, extractSampleRate(mimeType))
                    }
                }
            }

            return TtsSynthesisResult.Failure("Gemini audio decoding failure: No inline audio data found", false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Gemini audio response", e)
            return TtsSynthesisResult.Failure("Gemini audio decoding failure: ${e.message}", false)
        }
    }

    private suspend fun playAudioBytes(
        audio: CachedGeminiAudio,
        ttsId: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean {
        val isRawPcm = audio.mimeType.startsWith("audio/pcm") || audio.mimeType.startsWith("audio/l16")
        return if (isRawPcm) {
            playPcmViaAudioTrack(audio.bytes, audio.sampleRate, ttsId, onStart, onDone, onError)
        } else {
            // Container payload (WAV/MP3): MediaPlayer path. Never AudioTrack.
            playContainerViaMediaPlayer(audio.bytes, ttsId, onStart, onDone, onError)
        }
    }

    /**
     * Raw 16-bit mono PCM playback through a single AudioTrack (MODE_STREAM).
     *
     * Expected format where the API returns it: 24000 Hz, MONO, PCM 16-bit.
     * The actual MIME rate is honored when present. Completion fires only
     * after the full PCM drained through the track (TTS_COMPLETED) — never
     * via a fixed delay.
     */
    private suspend fun playPcmViaAudioTrack(
        pcmBytes: ByteArray,
        sampleRate: Int,
        ttsId: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        stopLocked()
        
        Log.i(TAG, "SAMPLE_RATE=$sampleRate")
        Log.i(TAG, "CHANNELS=1")
        Log.i(TAG, "PCM_16BIT ttsId=$ttsId bytes=${pcmBytes.size}")

        val minBuffer = try {
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack buffer query failed", e)
            AudioTrack.ERROR_BAD_VALUE
        }
        if (minBuffer <= 0) {
            val err = "AudioTrack unavailable on this device"
            Log.e(TAG, "TTS_ERROR provider=gemini model=tts voice=- ttsId=$ttsId category=$err")
            _ttsState.value = GeminiTtsState.ERROR
            withContext(Dispatchers.Main) { onError?.invoke(err) }
            return@withContext false
        }
        val bufferSize = maxOf(minBuffer * 4, 16384)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        // Same usage as the MediaPlayer path and audio-focus
                        // request so ducking/Bluetooth behave identically.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AUDIOTRACK init failed", e)
            _ttsState.value = GeminiTtsState.ERROR
            withContext(Dispatchers.Main) { onError?.invoke("Audio playback init failed") }
            return@withContext false
        }

        synchronized(audioOwnerLock) { activeTrack = track }
        requestAudioFocus()
        Log.i(TAG, "AUDIOTRACK_INITIALIZED ttsId=$ttsId rate=$sampleRate buffer=$bufferSize mode=MODE_STREAM")

        try {
            _ttsState.value = GeminiTtsState.PLAYING
            track.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack.play failed", e)
            releaseTrackLocked()
            abandonAudioFocus()
            _ttsState.value = GeminiTtsState.ERROR
            withContext(Dispatchers.Main) { onError?.invoke("Audio playback failed") }
            return@withContext false
        }

        withContext(Dispatchers.Main) { onStart?.invoke() }
        Log.i(TAG, "PLAYBACK_STARTED ttsId=$ttsId")

        // Stream the complete PCM; write() blocks until the hardware drains,
        // so returning from the loop means the audio actually finished.
        var offset = 0
        while (offset < pcmBytes.size) {
            val chunk = minOf(4096, pcmBytes.size - offset)
            val written = try {
                track.write(pcmBytes, offset, chunk)
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack.write failed", e)
                AudioTrack.ERROR
            }
            if (written < 0) {
                Log.e(TAG, "TTS_ERROR provider=gemini ttsId=$ttsId category=audiotrack_write_$written")
                break
            }
            offset += written
        }

        if (offset < pcmBytes.size) {
            // Interrupted via stop(): owner already released there.
            _ttsState.value = GeminiTtsState.IDLE
            withContext(Dispatchers.Main) { onDone?.invoke() }
            return@withContext true
        }

        // Drain: wait until the hardware actually played the frames written.
        try {
            val totalFrames = (pcmBytes.size / 2).toLong()
            val deadline = System.currentTimeMillis() + 30_000L
            while (System.currentTimeMillis() < deadline) {
                val head = try { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL } catch (e: Exception) { totalFrames }
                if (head >= totalFrames) break
                kotlinx.coroutines.delay(25)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PCM drain wait interrupted", e)
        }

        try { track.stop() } catch (e: Exception) { Log.w(TAG, "AudioTrack.stop failed", e) }
        releaseTrackLocked()
        abandonAudioFocus()
        _ttsState.value = GeminiTtsState.COMPLETED
        Log.i(TAG, "PLAYBACK_COMPLETED ttsId=$ttsId")
        Log.i(TAG, "TTS_COMPLETED ttsId=$ttsId")
        withContext(Dispatchers.Main) { onDone?.invoke() }
        return@withContext true
    }

    /** WAV/MP3 container playback (MediaPlayer). Never used for raw PCM. */
    private suspend fun playContainerViaMediaPlayer(
        audioBytes: ByteArray,
        ttsId: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.Main) {
        stopLocked()
        _ttsState.value = GeminiTtsState.PLAYING

        // Per-turn temp file (deleted on completion) so a stale buffer can
        // never leak into another response.
        val tempFile = try {
            val safeId = ttsId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(context.cacheDir, "gemini_tts_$safeId.wav")
            FileOutputStream(file).use { it.write(audioBytes) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write temp audio file for playback", e)
            _ttsState.value = GeminiTtsState.ERROR
            onError?.invoke("Gemini audio decoding failure: Could not buffer audio")
            return@withContext false
        }

        requestAudioFocus()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    abandonAudioFocus()
                    releasePlayerLocked()
                    runCatching { tempFile.delete() }
                    _ttsState.value = GeminiTtsState.COMPLETED
                    Log.i(TAG, "PLAYBACK_COMPLETED ttsId=$ttsId")
                    Log.i(TAG, "TTS_COMPLETED ttsId=$ttsId")
                    onDone?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    ttsError(provider = "gemini", model = "tts", voice = "-", category = "mediaplayer_$what")
                    abandonAudioFocus()
                    releasePlayerLocked()
                    runCatching { tempFile.delete() }
                    _ttsState.value = GeminiTtsState.ERROR
                    onError?.invoke("Audio playback error (code $what)")
                    true
                }
            }

            synchronized(audioOwnerLock) { activeMediaPlayer = player }
            Log.i(TAG, "PLAYBACK_STARTED ttsId=$ttsId engine=MediaPlayer")
            player.start()
            onStart?.invoke()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for playback", e)
            abandonAudioFocus()
            releasePlayerLocked()
            _ttsState.value = GeminiTtsState.ERROR
            onError?.invoke("Gemini audio decoding failure: ${e.message}")
            false
        }
    }

    override fun stop() {
        stopLocked()
        abandonAudioFocus()
        if (_ttsState.value == GeminiTtsState.PLAYING || _ttsState.value == GeminiTtsState.GENERATING) {
            _ttsState.value = GeminiTtsState.IDLE
        }
    }

    /** Releases whichever single owner is active (track XOR player). */
    private fun stopLocked() {
        synchronized(audioOwnerLock) {
            try {
                activeTrack?.let { track ->
                    try {
                        track.pause()
                        track.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping AudioTrack", e)
                    }
                    try { track.release() } catch (e: Exception) { Log.w(TAG, "Error releasing AudioTrack", e) }
                    activeTrack = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping active audio playback", e)
            }
            try {
                activeMediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping active audio playback", e)
            } finally {
                releasePlayerLocked()
            }
        }
    }

    private fun releaseTrackLocked() {
        synchronized(audioOwnerLock) {
            try {
                activeTrack?.release()
            } catch (e: Exception) {
                // Ignore
            }
            activeTrack = null
        }
    }

    private fun releasePlayerLocked() {
        synchronized(audioOwnerLock) {
            try {
                activeMediaPlayer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            activeMediaPlayer = null
        }
    }

    /**
     * Controlled failure log. Carries provider/model/voice/category only —
     * API keys, auth headers and tokens are NEVER logged.
     */
    private fun ttsError(provider: String, model: String, voice: String, category: String, httpCode: Int? = null) {
        val code = if (httpCode != null) " http=$httpCode" else ""
        Log.e(TAG, "TTS_ERROR provider=$provider model=$model voice=$voice$code category=$category")
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not abandon audio focus", e)
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun extractSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000
    }

    /**
     * Converts raw 16-bit Mono PCM bytes into a standard RIFF/WAVE headered format.
     */
    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }

    private sealed class TtsSynthesisResult {
        data class Success(val audioBytes: ByteArray, val mimeType: String, val sampleRate: Int = 24000) : TtsSynthesisResult()
        data class ModelNotFound(val model: String) : TtsSynthesisResult()
        data class Failure(val reason: String, val shouldHaltChain: Boolean) : TtsSynthesisResult()
    }

    companion object {
        private const val TAG = "GeminiTtsEngine"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Centralized Gemini TTS lifecycle states. */
enum class GeminiTtsState {
    IDLE,
    GENERATING,
    PLAYING,
    COMPLETED,
    ERROR
}

/** Cached synthesis: raw bytes kept with their MIME + rate so PCM stays PCM. */
data class CachedGeminiAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val sampleRate: Int = 24000
)
