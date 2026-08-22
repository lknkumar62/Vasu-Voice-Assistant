package com.vasu.ai.core

enum class VasuConversationState {
    IDLE,
    LISTENING,
    PROCESSING,
    EXECUTING,
    WAITING_FOR_FOLLOW_UP,
    COMPLETED,
    FAILED
}
