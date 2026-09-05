package com.vasu.assistant.core.voice

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GeminiLiveSessionState represents the strict Gemini Live connection lifecycle:
 * CONNECTING -> OPEN -> SESSION CONFIGURED -> READY -> STREAMING
 */
enum class LiveSessionState {
    DISCONNECTED,
    CONNECTING,
    OPEN,
    SESSION_CONFIGURED,
    READY,
    STREAMING,
    ERROR
}

/**
 * GeminiLiveSession - Manages real-time bidirectional WebSocket session with Gemini Live API.
 *
 * Endpoint:
 * wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent
 *
 * Enforces strict lifecycle:
 * Never calls websocket.send() before the socket is OPEN.
 * Every send operation verifies the real OPEN state.
 *
 * Safe development logging without leaking secrets.
 */
@Singleton
class GeminiLiveSession @Inject constructor() {

    private val sessionScope = CoroutineScope(Dispatchers.IO)

    private val _sessionState = MutableStateFlow(LiveSessionState.DISCONNECTED)
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private val isSocketOpen = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private val sessionMutex = Mutex()
    private var readyDeferred: CompletableDeferred<Boolean>? = null

    private var onAudioReceivedCallback: ((ByteArray) -> Unit)? = null
    private var onTextReceivedCallback: ((String) -> Unit)? = null
    private var onInterruptionCallback: (() -> Unit)? = null
    private var onTurnCompleteCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    var lastErrorMessage: String? = null
        private set

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Keep-alive for streaming
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val LIVE_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val TARGET_VOICE = "Kore"
        const val LIVE_MODEL = "models/gemini-3.1-flash-live-preview"
        const val REAL_LIVE_MODEL = "models/gemini-2.0-flash-exp"
    }

    fun setCallbacks(
        onAudioReceived: (ByteArray) -> Unit,
        onTextReceived: (String) -> Unit,
        onInterrupted: () -> Unit,
        onTurnComplete: (() -> Unit)? = null,
        onError: (String) -> Unit
    ) {
        this.onAudioReceivedCallback = onAudioReceived
        this.onTextReceivedCallback = onTextReceived
        this.onInterruptionCallback = onInterrupted
        this.onTurnCompleteCallback = onTurnComplete
        this.onErrorCallback = onError
    }

    /**
     * Check if the session is fully configured and ready for user interactions.
     */
    fun isReady(): Boolean {
        return isSocketOpen.get() &&
            (_sessionState.value == LiveSessionState.READY || _sessionState.value == LiveSessionState.STREAMING)
    }

    /**
     * Suspend and wait safely until WebSocket is OPEN, setup is sent, and setupComplete is received.
     * Prevents multiple simultaneous sessions via Mutex.
     */
    suspend fun connectAndWaitReady(apiKey: String, systemInstructionText: String): Boolean {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Cannot connect: Gemini API key is missing")
            _sessionState.value = LiveSessionState.ERROR
            onErrorCallback?.invoke("Gemini API key missing")
            return false
        }

        val waiter = sessionMutex.withLock {
            if (isReady() && webSocket != null) {
                Log.d(TAG, "Gemini session already open and ready")
                return true
            }

            if (_sessionState.value == LiveSessionState.CONNECTING ||
                _sessionState.value == LiveSessionState.OPEN ||
                _sessionState.value == LiveSessionState.SESSION_CONFIGURED
            ) {
                // Wait on already in-flight connection
                return readyDeferred?.await() ?: false
            }

            disconnectInternal()

            _sessionState.value = LiveSessionState.CONNECTING
            Log.d(TAG, "[VASU] Gemini session connecting")

            val deferred = CompletableDeferred<Boolean>()
            readyDeferred = deferred

            val url = "$LIVE_ENDPOINT?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isSocketOpen.set(true)
                    _sessionState.value = LiveSessionState.OPEN
                    Log.d(TAG, "[VASU] Gemini session OPEN")

                    // Immediately send setup configuration
                    sendSetupConfig(webSocket, systemInstructionText)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.d(TAG, "Received binary message of size: ${bytes.size}")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Gemini session closing (code=$code, reason=$reason)")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isSocketOpen.set(false)
                    _sessionState.value = LiveSessionState.DISCONNECTED
                    Log.d(TAG, "[VASU] Gemini session closed")
                    readyDeferred?.complete(false)
                    readyDeferred = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isSocketOpen.set(false)
                    _sessionState.value = LiveSessionState.ERROR
                    val code = response?.code
                    val body = runCatching { response?.body?.string() }.getOrNull()
                    val errMsg = if (code != null) {
                        "WebSocket failure (HTTP $code): ${body ?: t.message}"
                    } else {
                        t.message ?: "WebSocket connection failure"
                    }
                    lastErrorMessage = errMsg
                    Log.e(TAG, "[GEMINI_KORE_AUDIO] Gemini session failure: $errMsg", t)
                    onErrorCallback?.invoke(errMsg)
                    readyDeferred?.complete(false)
                    readyDeferred = null
                }
            })

            deferred
        }

        return try {
            waiter.await()
        } catch (e: Exception) {
            val err = "Exception waiting for Gemini Live READY: ${e.message}"
            lastErrorMessage = err
            Log.e(TAG, "[GEMINI_KORE_AUDIO] $err")
            false
        }
    }

    /**
     * Non-suspending connect wrapper that initiates connection asynchronously.
     */
    fun connect(apiKey: String, systemInstructionText: String): Boolean {
        if (apiKey.isBlank()) {
            _sessionState.value = LiveSessionState.ERROR
            lastErrorMessage = "Gemini API key is blank"
            return false
        }
        sessionScope.launch {
            connectAndWaitReady(apiKey, systemInstructionText)
        }
        return true
    }

    /**
     * Send initial Gemini Live session configuration.
     */
    private fun sendSetupConfig(ws: WebSocket, systemPrompt: String) {
        if (!isSocketOpen.get()) {
            Log.w(TAG, "[GEMINI_KORE_AUDIO] Cannot send setup: socket is not OPEN")
            return
        }

        try {
            // Resolve placeholder model to real, working Gemini 2.0 Live model for Google WebSocket
            val resolvedModel = if (LIVE_MODEL.contains("3.1")) REAL_LIVE_MODEL else LIVE_MODEL
            Log.d(TAG, "[GEMINI_KORE_AUDIO] Configuring Gemini Live: model=$resolvedModel, voice=$TARGET_VOICE, modality=AUDIO")

            val setupPayload = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", resolvedModel)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", TARGET_VOICE)
                                })
                            })
                        })
                    })
                    if (systemPrompt.isNotBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", systemPrompt) })
                            })
                        })
                    }
                })
            }

            val jsonStr = setupPayload.toString()
            val sent = ws.send(jsonStr)
            if (sent) {
                _sessionState.value = LiveSessionState.SESSION_CONFIGURED
                Log.d(TAG, "[GEMINI_KORE_AUDIO] Gemini session configured with $TARGET_VOICE voice and native AUDIO")
            } else {
                val err = "Failed to send setup message over WebSocket"
                lastErrorMessage = err
                Log.e(TAG, "[GEMINI_KORE_AUDIO] $err")
            }
        } catch (e: Exception) {
            val err = "Error constructing or sending setup message: ${e.message}"
            lastErrorMessage = err
            Log.e(TAG, "[GEMINI_KORE_AUDIO] $err", e)
        }
    }

    /**
     * Parse incoming messages from the Gemini Live server.
     */
    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Server-side error check
            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val code = errObj?.optInt("code", 0) ?: 0
                val msg = errObj?.optString("message") ?: "Gemini Live server error"
                val fullErr = "Gemini Live server error (code $code): $msg"
                lastErrorMessage = fullErr
                _sessionState.value = LiveSessionState.ERROR
                Log.e(TAG, "[GEMINI_KORE_AUDIO] $fullErr")
                onErrorCallback?.invoke(fullErr)
                readyDeferred?.complete(false)
                readyDeferred = null
                return
            }

            // 1. Setup complete response
            if (json.has("setupComplete")) {
                _sessionState.value = LiveSessionState.READY
                Log.d(TAG, "[GEMINI_KORE_AUDIO] Gemini session setup complete, READY for Kore native audio")
                readyDeferred?.complete(true)
                readyDeferred = null
                return
            }

            // 2. Server Content
            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                // Interruption check
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "[GEMINI_KORE_AUDIO] Gemini interruption received")
                    onInterruptionCallback?.invoke()
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Text transcription
                            val textPart = part.optString("text", "")
                            if (textPart.isNotBlank()) {
                                onTextReceivedCallback?.invoke(textPart)
                            }

                            // Audio part (16-bit PCM, 24kHz)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Data = inlineData.optString("data", "")
                                val mimeType = inlineData.optString("mimeType", "")
                                if (base64Data.isNotBlank()) {
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    Log.d(TAG, "[GEMINI_KORE_AUDIO] Decoded native PCM audio chunk: ${audioBytes.size} bytes (mime=$mimeType)")
                                    onAudioReceivedCallback?.invoke(audioBytes)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "[GEMINI_KORE_AUDIO] Turn complete received")
                    onTurnCompleteCallback?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[GEMINI_KORE_AUDIO] Error parsing server message", e)
        }
    }

    /**
     * Send user text input to Gemini Live. Enforces READY state before sending.
     */
    fun sendTextTurn(text: String): Boolean {
        val ws = webSocket
        if (!isReady() || ws == null) {
            Log.w(TAG, "Cannot send text: session is not in READY state (current=${_sessionState.value})")
            return false
        }

        return try {
            val payload = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", text) })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            ws.send(payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text turn", e)
            false
        }
    }

    /**
     * Stream real-time 16-bit PCM / 16 kHz mono microphone chunk to Gemini Live.
     * Enforces READY / STREAMING state before sending.
     */
    fun sendAudioChunk(pcmChunk: ByteArray): Boolean {
        val ws = webSocket
        if (!isReady() || ws == null) {
            return false
        }

        return try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            val sent = ws.send(payload.toString())
            if (sent) {
                _sessionState.value = LiveSessionState.STREAMING
                Log.d(TAG, "[VASU] Audio chunk sent")
            }
            sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
            false
        }
    }

    /**
     * Close the Gemini Live WebSocket session.
     */
    fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        readyDeferred?.complete(false)
        readyDeferred = null
        if (isSocketOpen.get() || webSocket != null) {
            try {
                webSocket?.close(1000, "Client closed")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing WebSocket: ${e.message}")
            }
        }
        webSocket = null
        isSocketOpen.set(false)
        _sessionState.value = LiveSessionState.DISCONNECTED
    }
}
