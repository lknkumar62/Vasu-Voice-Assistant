package com.vasu.assistant.core.voice

/**
 * Real-time conversational voice states exposed to the UI and orb.
 */
enum class GeminiVoiceState {
    IDLE,
    CONNECTING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    DISCONNECTED,
    ERROR
}
