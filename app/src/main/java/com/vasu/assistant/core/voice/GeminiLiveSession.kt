package com.vasu.assistant.core.voice

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GeminiLiveSession - Bidirectional real-time WebSocket session coordinator
 * for Google Gemini Live Multimodal Audio API.
 *
 * Voice: "Kore"
 * Audio In: 16-bit Mono PCM @ 16 kHz
 * Audio Out: 16-bit Mono PCM @ 24 kHz
 */
@Singleton
class GeminiLiveSession @Inject constructor() {

    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val WS_HOST = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val TARGET_VOICE = "Kore"
        const val LIVE_MODEL = "models/gemini-2.0-flash-exp"
    }

    private var onAudioReceived: ((ByteArray) -> Unit)? = null
    private var onTextReceived: ((String) -> Unit)? = null
    private var onInterrupted: (() -> Unit)? = null
    private var onTurnComplete: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val isConnected = AtomicBoolean(false)
    private val isReadyState = AtomicBoolean(false)

    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var readyDeferred: CompletableDeferred<Boolean>? = null

    fun setCallbacks(
        onAudioReceived: (ByteArray) -> Unit,
        onTextReceived: (String) -> Unit,
        onInterrupted: () -> Unit,
        onTurnComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        this.onAudioReceived = onAudioReceived
        this.onTextReceived = onTextReceived
        this.onInterrupted = onInterrupted
        this.onTurnComplete = onTurnComplete
        this.onError = onError
    }

    fun isReady(): Boolean = isConnected.get() && isReadyState.get()

    /**
     * Connect to Gemini Live WebSocket and suspend until setup handshake completes.
     */
    suspend fun connectAndWaitReady(apiKey: String, systemInstruction: String): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        val deferred = CompletableDeferred<Boolean>()
        readyDeferred = deferred

        try {
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            okHttpClient = client

            val url = "$WS_HOST?key=${apiKey.trim()}"
            val request = Request.Builder().url(url).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(TAG, "Gemini Live WebSocket opened; sending setup payload")
                    isConnected.set(true)
                    val setupMessage = buildSetupMessage(systemInstruction)
                    ws.send(setupMessage)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncomingServerMessage(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Gemini Live closing: $code / $reason")
                    isConnected.set(false)
                    isReadyState.set(false)
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Gemini Live closed: $code / $reason")
                    isConnected.set(false)
                    isReadyState.set(false)
                    readyDeferred?.complete(false)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    val err = t.message ?: "WebSocket failure"
                    Log.e(TAG, "Gemini Live failure: $err", t)
                    isConnected.set(false)
                    isReadyState.set(false)
                    readyDeferred?.complete(false)
                    onError?.invoke(err)
                }
            })

            val ready = withTimeoutOrNull(8000) {
                deferred.await()
            } ?: false

            if (!ready) {
                Log.w(TAG, "Timed out waiting for Gemini Live setup confirmation")
            }
            return@withContext ready
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to Gemini Live", e)
            readyDeferred?.complete(false)
            onError?.invoke(e.message ?: "Failed to connect to Gemini Live")
            return@withContext false
        }
    }

    private fun buildSetupMessage(systemInstruction: String): String {
        val root = JSONObject()
        val setup = JSONObject()
        setup.put("model", LIVE_MODEL)

        val generationConfig = JSONObject()
        val responseModalities = JSONArray()
        responseModalities.put("AUDIO")
        generationConfig.put("responseModalities", responseModalities)

        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", TARGET_VOICE) // "Kore"
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        generationConfig.put("speechConfig", speechConfig)

        setup.put("generationConfig", generationConfig)

        if (systemInstruction.isNotBlank()) {
            val systemInstructionObj = JSONObject()
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", systemInstruction)
            parts.put(part)
            systemInstructionObj.put("parts", parts)
            setup.put("systemInstruction", systemInstructionObj)
        }

        root.put("setup", setup)
        return root.toString()
    }

    private fun handleIncomingServerMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // Setup complete handshake
            if (root.has("setupComplete")) {
                Log.d(TAG, "Gemini Live session setupComplete received - session is READY")
                isReadyState.set(true)
                readyDeferred?.complete(true)
                return
            }

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server reported user interrupted assistant")
                    onInterrupted?.invoke()
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Native AUDIO chunk
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Audio = inlineData.optString("data", "")
                                if (base64Audio.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    onAudioReceived?.invoke(pcmBytes)
                                }
                            }

                            // Model text transcript
                            if (part.has("text")) {
                                val text = part.optString("text", "")
                                if (text.isNotEmpty()) {
                                    onTextReceived?.invoke(text)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Model turn complete")
                    onTurnComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Gemini Live server payload", e)
        }
    }

    /**
     * Send user text prompt to Gemini Live to synthesize real native Kore audio response.
     */
    fun sendTextTurn(promptText: String): Boolean {
        if (!isReady()) {
            Log.w(TAG, "Cannot send text: session not ready")
            return false
        }
        return try {
            val root = JSONObject()
            val clientContent = JSONObject()
            val turns = JSONArray()
            val turn = JSONObject()
            turn.put("role", "user")
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", promptText)
            parts.put(part)
            turn.put("parts", parts)
            turns.put(turn)
            clientContent.put("turns", turns)
            clientContent.put("turnComplete", true)
            root.put("clientContent", clientContent)

            webSocket?.send(root.toString()) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text turn to Gemini Live", e)
            false
        }
    }

    /**
     * Stream user microphone audio (16-bit PCM @ 16 kHz mono) into Gemini Live session.
     */
    fun sendAudioChunk(pcmChunk: ByteArray): Boolean {
        if (!isReady() || pcmChunk.isEmpty()) return false
        return try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunk = JSONObject()
            chunk.put("mimeType", "audio/pcm;rate=16000")
            chunk.put("data", Base64.encodeToString(pcmChunk, Base64.NO_WRAP))
            mediaChunks.put(chunk)
            realtimeInput.put("mediaChunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)

            webSocket?.send(root.toString()) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending realtime audio chunk", e)
            false
        }
    }

    fun disconnect() {
        isConnected.set(false)
        isReadyState.set(false)
        readyDeferred?.complete(false)
        try {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket", e)
        }
    }
}
