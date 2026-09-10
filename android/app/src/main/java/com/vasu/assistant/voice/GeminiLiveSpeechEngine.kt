package com.vasu.assistant.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.vasu.assistant.config.GeminiVoiceConfig
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.util.concurrent.Executors

/**
 * Primary Online Speech Engine for VASU Assistant.
 * Uses Gemini Live native audio with the prebuilt Kore voice.
 * Automatically falls back to OfflineSpeechEngine when network is unavailable.
 */
class GeminiLiveSpeechEngine(
    private val context: Context,
    private var apiKey: String
) : VasuSpeechEngine {

    private val tag = "GeminiLiveSpeechEngine"

    private val audioPlayer = GeminiAudioPlayer(context)
    private val offlineEngine = OfflineSpeechEngine(context)
    private val executor = Executors.newSingleThreadExecutor()

    private var activeSpeechCallbackEnd: (() -> Unit)? = null
    private var quotaExhaustedUntil: Long = 0L

    fun setApiKey(key: String) {
        this.apiKey = key.trim()
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun speak(text: String, onStart: (() -> Unit)?, onEnd: (() -> Unit)?) {
        // Stop any current playback
        stop()

        val cleanText = text.replace(Regex("[*_~`#]"), "").trim()
        if (cleanText.isEmpty()) {
            onEnd?.invoke()
            return
        }

        // Check if offline, missing key, or currently in quota cooldown
        if (!isOnline() || apiKey.length < 8 || System.currentTimeMillis() < quotaExhaustedUntil) {
            Log.d(tag, "Using offline speech engine (network unavailable, missing key, or quota cooldown active)")
            offlineEngine.speak(cleanText, onStart, onEnd)
            return
        }

        activeSpeechCallbackEnd = onEnd
        onStart?.invoke()

        // Fetch native Gemini audio with voice Kore
        executor.execute {
            try {
                val pcmAudio = requestGeminiNativeAudio(cleanText, apiKey)
                if (pcmAudio != null && pcmAudio.isNotEmpty()) {
                    audioPlayer.enqueueAudio(pcmAudio, onEnd)
                } else {
                    Log.w(tag, "Gemini audio generation returned empty, falling back to offline engine")
                    offlineEngine.speak(cleanText, onStart, onEnd)
                }
            } catch (e: Exception) {
                Log.e(tag, "Gemini speech request failed, falling back", e)
                offlineEngine.speak(cleanText, onStart, onEnd)
            }
        }
    }

    /**
     * Request native audio synthesis from Gemini API with voiceName = "Kore".
     */
    private fun requestGeminiNativeAudio(text: String, key: String): ByteArray? {
        val models = listOf("gemini-3.1-flash-tts-preview")
        for (model in models) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

                val root = JSONObject()
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()
                val part = JSONObject()
                part.put("text", text)
                parts.put(part)
                contentObj.put("parts", parts)
                contents.put(contentObj)
                root.put("contents", contents)

                val genConfig = JSONObject()
                val modalities = JSONArray()
                modalities.put("AUDIO")
                genConfig.put("responseModalities", modalities)

                val speechConfig = JSONObject()
                val voiceConfig = JSONObject()
                val prebuiltVoiceConfig = JSONObject()
                prebuiltVoiceConfig.put("voiceName", GeminiVoiceConfig.VOICE_NAME) // "Kore"
                voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                speechConfig.put("voiceConfig", voiceConfig)
                genConfig.put("speechConfig", speechConfig)

                root.put("generationConfig", genConfig)

                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 3000
                conn.readTimeout = 5000
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code == 200) {
                    val responseString = conn.inputStream.bufferedReader().use { it.readText() }
                    val respJson = JSONObject(responseString)
                    val candidates = respJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val candidateContent = firstCandidate?.optJSONObject("content")
                    val candidateParts = candidateContent?.optJSONArray("parts")
                    val firstPart = candidateParts?.optJSONObject(0)
                    val inlineData = firstPart?.optJSONObject("inlineData")
                    val base64Data = inlineData?.optString("data", "")

                    if (!base64Data.isNullOrEmpty()) {
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                } else {
                    if (code == 429) {
                        Log.i(tag, "Gemini TTS quota limit reached (429), cooling down cloud TTS for 30 minutes and using offline speech")
                        quotaExhaustedUntil = System.currentTimeMillis() + 1800_000L
                    }
                    Log.i(tag, "Gemini API with $model returned HTTP $code, attempting fallback")
                }
            } catch (e: Exception) {
                Log.w(tag, "TTS generation error on $model", e)
            }
        }
        return null
    }

    override fun stop() {
        audioPlayer.interrupt()
        offlineEngine.stop()
    }

    override fun isSpeaking(): Boolean {
        return audioPlayer.isCurrentlyPlaying() || offlineEngine.isSpeaking()
    }

    override fun release() {
        stop()
        audioPlayer.release()
        offlineEngine.release()
    }
}
