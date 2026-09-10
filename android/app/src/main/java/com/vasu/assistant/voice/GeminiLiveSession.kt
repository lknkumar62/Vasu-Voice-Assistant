package com.vasu.assistant.voice

import android.util.Base64
import android.util.Log
import com.vasu.assistant.config.GeminiVoiceConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages real-time bidirectional WebSocket session with Gemini Live API.
 * Configures the Kore prebuilt voice and native audio streaming.
 */
class GeminiLiveSession(
    private var apiKey: String,
    private val listener: LiveSessionListener
) {
    private val tag = "GeminiLiveSession"

    interface LiveSessionListener {
        fun onConnected()
        fun onAudioData(pcmBytes: ByteArray)
        fun onInterrupted()
        fun onUserTranscript(text: String)
        fun onModelTranscript(text: String)
        fun onError(error: String)
        fun onClosed()
    }

    private val isConnected = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null

    fun setApiKey(key: String) {
        this.apiKey = key.trim()
    }

    /**
     * Connect to the Gemini Live bidirectional WebSocket endpoint.
     */
    fun connect(key: String = apiKey): Boolean {
        val effectiveKey = key.ifEmpty { apiKey }.trim()
        if (effectiveKey.isEmpty()) {
            listener.onError("API_KEY_MISSING: Cannot connect to Gemini Live without an API Key")
            return false
        }

        close()

        try {
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
            this.okHttpClient = client

            val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$effectiveKey"
            val request = Request.Builder().url(url).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(tag, "Gemini Live WebSocket opened successfully")
                    isConnected.set(true)
                    
                    // Send setup message immediately
                    val setupJson = createSetupMessage()
                    ws.send(setupJson)
                    listener.onConnected()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "Gemini Live WebSocket closing: $code / $reason")
                    isConnected.set(false)
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "Gemini Live WebSocket closed: $code / $reason")
                    isConnected.set(false)
                    listener.onClosed()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(tag, "Gemini Live WebSocket error: ${t.message}", t)
                    isConnected.set(false)
                    listener.onError(t.message ?: "Gemini Live WebSocket network failure")
                }
            })
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate Gemini Live WebSocket", e)
            listener.onError(e.message ?: "Failed to connect to Gemini Live")
            return false
        }
    }

    /**
     * Send user PCM audio chunk to Gemini Live.
     */
    fun sendAudioChunk(pcmBytes: ByteArray): Boolean {
        if (!isConnected.get() || webSocket == null) return false
        val msg = createAudioChunkMessage(pcmBytes)
        return webSocket?.send(msg) ?: false
    }

    /**
     * Send a user text message/turn to Gemini Live.
     */
    fun sendTextTurn(text: String): Boolean {
        if (!isConnected.get() || webSocket == null) return false
        try {
            val root = JSONObject()
            val clientContent = JSONObject()
            val turns = JSONArray()
            val turn = JSONObject()
            turn.put("role", "user")
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", text)
            parts.put(part)
            turn.put("parts", parts)
            turns.put(turn)
            clientContent.put("turns", turns)
            clientContent.put("turnComplete", true)
            root.put("clientContent", clientContent)
            return webSocket?.send(root.toString()) ?: false
        } catch (e: Exception) {
            Log.e(tag, "Error sending text turn", e)
            return false
        }
    }

    /**
     * Builds the initial setup message JSON for Gemini Live.
     * Sets voiceName = "Kore", responseModalities = ["AUDIO"].
     */
    fun createSetupMessage(): String {
        val root = JSONObject()
        val setup = JSONObject()

        setup.put("model", "models/${GeminiVoiceConfig.DEFAULT_MODEL}")

        val generationConfig = JSONObject()
        val responseModalities = JSONArray()
        responseModalities.put("AUDIO")
        generationConfig.put("responseModalities", responseModalities)

        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", GeminiVoiceConfig.VOICE_NAME) // "Kore"
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        generationConfig.put("speechConfig", speechConfig)

        setup.put("generationConfig", generationConfig)

        val systemInstruction = JSONObject()
        val parts = JSONArray()
        val part = JSONObject()
        part.put("text", GeminiVoiceConfig.SYSTEM_INSTRUCTION)
        parts.put(part)
        systemInstruction.put("parts", parts)
        setup.put("systemInstruction", systemInstruction)

        root.put("setup", setup)
        return root.toString()
    }

    /**
     * Creates the realtime audio chunk JSON message.
     */
    fun createAudioChunkMessage(pcmBytes: ByteArray): String {
        val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val root = JSONObject()
        val realtimeInput = JSONObject()
        val mediaChunks = JSONArray()
        val chunk = JSONObject()
        chunk.put("mimeType", GeminiVoiceConfig.INPUT_AUDIO_ENCODING)
        chunk.put("data", base64Data)
        mediaChunks.put(chunk)
        realtimeInput.put("mediaChunks", mediaChunks)
        root.put("realtimeInput", realtimeInput)
        return root.toString()
    }

    /**
     * Parses incoming JSON message from Gemini Live WebSocket.
     */
    fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Check for interruption signal from Gemini
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(tag, "Gemini Live: Model interrupted by user speech")
                    listener.onInterrupted()
                    return
                }

                // Check for model turn with audio parts
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Native audio data
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Audio = inlineData.optString("data", "")
                                if (base64Audio.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    listener.onAudioData(pcmBytes)
                                }
                            }

                            // Optional text transcription
                            if (part.has("text")) {
                                val textContent = part.optString("text", "")
                                if (textContent.isNotEmpty()) {
                                    listener.onModelTranscript(textContent)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Gemini Live message", e)
        }
    }

    fun isSessionActive(): Boolean = isConnected.get()

    fun close() {
        if (isConnected.getAndSet(false)) {
            try {
                webSocket?.close(1000, "Session closed")
                webSocket = null
            } catch (e: Exception) {
                Log.w(tag, "Error closing websocket", e)
            }
            listener.onClosed()
        }
    }
}
