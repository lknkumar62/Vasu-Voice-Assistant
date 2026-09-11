package com.vasu.assistant.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveChatManager @Inject constructor(
    private val webSocket: LiveChatWebSocket
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverUrl: String = LiveChatWebSocket.DEFAULT_SERVER
    private var sessionId: String? = null
    private var messageCallback: ((String, String) -> Unit)? = null

    fun initialize(
        serverUrl: String = LiveChatWebSocket.DEFAULT_SERVER,
        sessionId: String? = null,
        onMessage: (text: String, sender: String) -> Unit = { _, _ -> }
    ) {
        this.serverUrl = serverUrl
        this.sessionId = sessionId
        this.messageCallback = onMessage
        observeMessages()
    }

    private fun observeMessages() {
        scope.launch {
            webSocket.incomingMessages.collectLatest { event ->
                when (event) {
                    is LiveChatWebSocket.ChatEvent.TextMessage -> {
                        messageCallback?.invoke(event.text, event.sender)
                    }
                    is LiveChatWebSocket.ChatEvent.Connected -> {
                        Log.d(TAG, "Live chat connected")
                    }
                    is LiveChatWebSocket.ChatEvent.Disconnected -> {
                        Log.d(TAG, "Live chat disconnected")
                    }
                    is LiveChatWebSocket.ChatEvent.Error -> {
                        Log.e(TAG, "Live chat error: ${event.message}")
                    }
                    is LiveChatWebSocket.ChatEvent.TypingIndicator -> {
                        // Handle typing indicator
                    }
                }
            }
        }
    }

    fun connect() {
        webSocket.connect(serverUrl, sessionId)
    }

    fun disconnect() {
        webSocket.disconnect()
    }

    fun sendMessage(text: String) {
        webSocket.sendMessage(text, type = "chat")
    }

    fun sendVoiceTranscript(text: String) {
        webSocket.sendMessage(text, type = "voice")
    }

    fun isConnected(): Boolean = webSocket.isConnected()

    fun getState() = webSocket.connectionState

    companion object {
        private const val TAG = "LiveChatManager"
    }
}
