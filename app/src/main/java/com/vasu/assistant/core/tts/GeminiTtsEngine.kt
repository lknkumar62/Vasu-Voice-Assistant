package com.vasu.assistant.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
 * Capabilities:
 * - Uses actual Gemini speech generation models:
 *   Primary: gemini-3.1-flash-tts-preview
 *   Fallback 1: gemini-2.5-flash-preview-tts
 *   Fallback 2: gemini-2.0-flash
 * - Prebuilt natural female assistant voice: "Kore" (warm, conversational, friendly).
 * - Tailored Hindi-first assistant prompting for natural cadence and clear pronunciation.
 * - Handles inline audio response decoding (WAV, MP3, and raw 24kHz PCM).
 * - Integrated AudioFocus and interruption handling.
 * - In-memory LRU cache for short repeated responses.
 * - Clean error reporting (missing key, invalid key, rate limit, network unavailable, decoding error).
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

    // In-memory LRU cache for synthesized audio bytes (up to 25 recent phrases)
    private val audioMemoryCache = LruCache<String, ByteArray>(25)

    private var activeMediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override suspend fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        val speakable = toSpeakableText(text)
        if (speakable.isBlank()) {
            withContext(Dispatchers.Main) { onDone?.invoke() }
            return@withContext true
        }

        // 1. Connectivity check
        if (!isOnline() || settings.offlineOnly.value) {
            val err = "Gemini network unavailable"
            Log.w(TAG, "[GEMINI_KORE_AUDIO] $err")
            withContext(Dispatchers.Main) { onError?.invoke(err) }
            return@withContext false
        }

        // 2. API key check
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            val err = "Gemini API key missing"
            Log.w(TAG, "[GEMINI_KORE_AUDIO] $err")
            withContext(Dispatchers.Main) { onError?.invoke(err) }
            return@withContext false
        }

        // 3. Cache lookup
        val cacheKey = "${settings.geminiTtsVoice.value}_$speakable"
        val cachedAudio = audioMemoryCache.get(cacheKey)
        if (cachedAudio != null) {
            Log.d(TAG, "[GEMINI_KORE_AUDIO] Playing Gemini TTS from memory cache for: \"$speakable\"")
            return@withContext playAudioBytes(cachedAudio, onStart, onDone, onError)
        }

        // 4. Synthesize via Gemini API with model fallback chain
        val preferredModel = settings.geminiTtsModel.value
        val modelChain = linkedSetOf(
            "gemini-2.0-flash-exp",
            "gemini-2.0-flash",
            preferredModel,
            VasuSettings.DEFAULT_GEMINI_TTS_MODEL,
            VasuSettings.FALLBACK_GEMINI_TTS_MODEL,
            VasuSettings.BASE_GEMINI_TTS_MODEL
        ).toList()

        var lastErrorReason = "Gemini TTS synthesis failed"

        for (model in modelChain) {
            val synthesisResult = requestGeminiAudio(model, apiKey, speakable)
            when (synthesisResult) {
                is TtsSynthesisResult.Success -> {
                    Log.d(TAG, "[GEMINI_KORE_AUDIO] Successfully synthesized Kore audio using model: $model")
                    audioMemoryCache.put(cacheKey, synthesisResult.audioBytes)
                    return@withContext playAudioBytes(synthesisResult.audioBytes, onStart, onDone, onError)
                }
                is TtsSynthesisResult.ModelNotFound -> {
                    Log.w(TAG, "[GEMINI_KORE_AUDIO] Model $model not found or unsupported for TTS on this key, trying next fallback")
                    lastErrorReason = "Gemini TTS model unavailable"
                }
                is TtsSynthesisResult.Failure -> {
                    lastErrorReason = synthesisResult.reason
                    Log.w(TAG, "[GEMINI_KORE_AUDIO] Model $model failed: $lastErrorReason")
                    // Non-model errors (key rejected, quota exhausted, network dropped) should not cycle through all models
                    if (synthesisResult.shouldHaltChain) {
                        break
                    }
                }
            }
        }

        Log.e(TAG, "[GEMINI_KORE_AUDIO] Gemini TTS failed across all candidates: $lastErrorReason")
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
                    val processedBytes = if (mimeType.startsWith("audio/pcm")) {
                        val sampleRate = extractSampleRate(mimeType)
                        pcmToWav(rawBytes, sampleRate)
                    } else {
                        rawBytes
                    }
                    return TtsSynthesisResult.Success(processedBytes, mimeType)
                }
            }

            return TtsSynthesisResult.Failure("Gemini audio decoding failure: No inline audio data found", false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Gemini audio response", e)
            return TtsSynthesisResult.Failure("Gemini audio decoding failure: ${e.message}", false)
        }
    }

    private suspend fun playAudioBytes(
        audioBytes: ByteArray,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.Main) {
        stop()

        val tempFile = try {
            val file = File(context.cacheDir, "gemini_tts_temp.wav")
            FileOutputStream(file).use { it.write(audioBytes) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write temp audio file for playback", e)
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
                    releasePlayer()
                    onDone?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    abandonAudioFocus()
                    releasePlayer()
                    onError?.invoke("Audio playback error (code $what)")
                    true
                }
            }

            activeMediaPlayer = player
            player.start()
            onStart?.invoke()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for playback", e)
            abandonAudioFocus()
            releasePlayer()
            onError?.invoke("Gemini audio decoding failure: ${e.message}")
            false
        }
    }

    override fun stop() {
        try {
            activeMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping active audio playback", e)
        } finally {
            abandonAudioFocus()
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        try {
            activeMediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        activeMediaPlayer = null
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
        data class Success(val audioBytes: ByteArray, val mimeType: String) : TtsSynthesisResult()
        data class ModelNotFound(val model: String) : TtsSynthesisResult()
        data class Failure(val reason: String, val shouldHaltChain: Boolean) : TtsSynthesisResult()
    }

    companion object {
        private const val TAG = "GeminiTtsEngine"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
