package com.vasu.ai.core

enum class VasuWakeWordState {
    DISABLED,
    IDLE,
    STARTING,
    LISTENING_FOR_WAKE_WORD,
    WAKE_DETECTED,
    COMMAND_LISTENING,
    COMMAND_RECEIVED,
    PROCESSING,
    RECOVERING,
    STOPPING,
    ERROR
}
