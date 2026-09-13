package com.vasu.assistant.core.voice

/**
 * Real-time conversational voice states exposed to the UI and orb.
 */
enum class GeminiVoiceState {
    IDLE,
    BACKGROUND_LISTENING,
    WAKE_DETECTED,
    COMMAND_LISTENING,
    PROCESSING,
    SPEAKING,
    STOPPING,
    ERROR
}
