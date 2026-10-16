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
        const val TARGET_VOICE = GeminiLiveProtocol.TARGET_VOICE
        const val LIVE_MODEL = GeminiLiveProtocol.LIVE_MODEL
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
     * Guards every listener callback: late callbacks from a socket that was
     * superseded (model retry) or torn down must not touch shared state.
     * Without this, a previous attempt's onClosed/onFailure completed the NEW
     * attempt's deferred and clobbered isConnected (review finding 1).
     */
    private fun isStaleSocket(ws: WebSocket): Boolean = ws !== webSocket

    /**
     * Connect to Gemini Live WebSocket and suspend until setup handshake completes.
     */
    suspend fun connectAndWaitReady(
        apiKey: String,
        systemInstruction: String,
        model: String = GeminiLiveProtocol.LIVE_MODEL
    ): Boolean = withContext(Dispatchers.IO) {
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

            val url = "${GeminiLiveProtocol.WS_ENDPOINT}?key=${apiKey.trim()}"
            val request = Request.Builder().url(url).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    if (isStaleSocket(ws)) return
                    Log.d(TAG, "Gemini Live WebSocket opened; sending setup payload")
                    isConnected.set(true)
                    val setupMessage = GeminiLiveProtocol.setupMessage(
                        model = model,
                        voiceName = GeminiLiveProtocol.TARGET_VOICE,
                        systemInstruction = systemInstruction
                    )
                    ws.send(setupMessage)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    if (isStaleSocket(ws)) return
                    handleIncomingServerMessage(text)
                }

                override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                    if (isStaleSocket(ws)) return
                    // Gemini Live delivers server messages (including setupComplete)
                    // as BINARY WebSocket frames. Without this override every message
                    // was silently dropped and the setup handshake never completed
                    // (FIX-12 root cause).
                    handleIncomingServerMessage(bytes.utf8())
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    if (isStaleSocket(ws)) return
                    Log.d(TAG, "Gemini Live closing: $code / $reason")
                    isConnected.set(false)
                    isReadyState.set(false)
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    if (isStaleSocket(ws)) return
                    Log.d(TAG, "Gemini Live closed: $code / $reason")
                    isConnected.set(false)
                    isReadyState.set(false)
                    readyDeferred?.complete(false)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    if (isStaleSocket(ws)) return
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
                // Close this attempt's socket so a late close-ack cannot interfere
                // with the next attempt (model retry - review finding 1).
                disconnect()
            }
            return@withContext ready
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to Gemini Live", e)
            readyDeferred?.complete(false)
            onError?.invoke(e.message ?: "Failed to connect to Gemini Live")
            return@withContext false
        }
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
            webSocket?.send(GeminiLiveProtocol.textTurnMessage(promptText)) ?: false
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
            webSocket?.send(GeminiLiveProtocol.audioChunkMessage(pcmChunk)) ?: false
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
