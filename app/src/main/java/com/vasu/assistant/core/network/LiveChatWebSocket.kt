package com.vasu.assistant.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveChatWebSocket @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var reconnectAttempts = 0
    private var maxReconnectAttempts = 5

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 50)
    val incomingMessages: SharedFlow<ChatEvent> = _incomingMessages.asSharedFlow()

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
    }

    sealed class ChatEvent {
        data class TextMessage(val text: String, val sender: String = "server") : ChatEvent()
        data class TypingIndicator(val isTyping: Boolean) : ChatEvent()
        data class Error(val message: String) : ChatEvent()
        object Connected : ChatEvent()
        object Disconnected : ChatEvent()
    }

    fun connect(serverUrl: String, sessionId: String? = null) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Connecting to: $serverUrl")

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val url = if (sessionId != null) "$serverUrl?session=$sessionId" else serverUrl
        val request = Request.Builder()
            .url(url)
            .header("X-Client", "VASU-Android")
            .header("X-Version", "1.0")
            .build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                scope.launch {
                    _incomingMessages.emit(ChatEvent.Connected)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: ${text.take(100)}")
                scope.launch {
                    try {
                        val event = parseMessage(text)
                        _incomingMessages.emit(event)
                    } catch (e: Exception) {
                        _incomingMessages.emit(ChatEvent.Error("Parse error: ${e.message}"))
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                scope.launch {
                    _incomingMessages.emit(ChatEvent.Disconnected)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.ERROR
                scope.launch {
                    _incomingMessages.emit(ChatEvent.Error(t.message ?: "Connection failed"))
                }
                attemptReconnect(serverUrl, sessionId)
            }
        })
    }

    fun sendMessage(text: String, type: String = "chat") {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send: not connected")
            return
        }

        val message = JSONObject().apply {
            put("type", type)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }

        val sent = webSocket?.send(message.toString())
        if (sent == true) {
            Log.d(TAG, "Message sent: ${text.take(50)}")
        } else {
            Log.e(TAG, "Failed to send message")
        }
    }

    fun sendTypingIndicator(isTyping: Boolean) {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        val message = JSONObject().apply {
            put("type", "typing")
            put("is_typing", isTyping)
        }
        webSocket?.send(message.toString())
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting")
        reconnectAttempts = maxReconnectAttempts // Prevent reconnect
        webSocket?.close(1000, "Client disconnect")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun attemptReconnect(serverUrl: String, sessionId: String?) {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts reached")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        _connectionState.value = ConnectionState.RECONNECTING
        reconnectAttempts++

        val delayMs = (1000L * reconnectAttempts).coerceAtMost(30000L)
        Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")

        scope.launch {
            delay(delayMs)
            connect(serverUrl, sessionId)
        }
    }

    private fun parseMessage(text: String): ChatEvent {
        return try {
            val json = JSONObject(text)
            when (json.optString("type", "message")) {
                "message" -> ChatEvent.TextMessage(
                    text = json.optString("text", ""),
                    sender = json.optString("sender", "server")
                )
                "typing" -> ChatEvent.TypingIndicator(
                    isTyping = json.optBoolean("is_typing", false)
                )
                "error" -> ChatEvent.Error(
                    message = json.optString("message", "Unknown error")
                )
                else -> ChatEvent.TextMessage(text = text)
            }
        } catch (e: Exception) {
            ChatEvent.TextMessage(text = text)
        }
    }

    fun sendMessageWithPayload(payload: Map<String, Any>) {
        val json = JSONObject(payload)
        webSocket?.send(json.toString())
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    companion object {
        private const val TAG = "LiveChatWebSocket"

        // Default VASU backend URL
        const val DEFAULT_SERVER = "wss://chat.vasu.app/ws"
    }
}
