package com.vasu.assistant.core.wakeword

/**
 * WakeWordState - Lifecycle and operational states of the wake-word detector.
 */
enum class WakeWordState {
    IDLE,
    LISTENING,
    DETECTED,
    MODEL_NOT_AVAILABLE,
    ERROR
}
