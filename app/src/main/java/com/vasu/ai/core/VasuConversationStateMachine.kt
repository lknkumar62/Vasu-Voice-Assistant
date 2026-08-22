package com.vasu.ai.core

class VasuConversationStateMachine(
    private val store: VasuConversationContextStore
) {
    fun currentState(): VasuConversationState = store.get().state

    fun startListening() = transition(VasuConversationState.LISTENING)
    fun startProcessing() = transition(VasuConversationState.PROCESSING)
    fun startExecuting() = transition(VasuConversationState.EXECUTING)
    fun waitForFollowUp() = transition(VasuConversationState.WAITING_FOR_FOLLOW_UP)
    fun complete() = transition(VasuConversationState.COMPLETED)
    fun fail() = transition(VasuConversationState.FAILED)
    fun reset() = store.clear()

    private fun transition(next: VasuConversationState) {
        val current = store.get().state
        if (!isAllowed(current, next)) {
            println("VASU_CONVERSATION_STATE blocked=$current->$next")
            return
        }
        store.updateState(next)
        println("VASU_CONVERSATION_STATE $current->$next")
    }

    private fun isAllowed(from: VasuConversationState, to: VasuConversationState): Boolean = when (from) {
        VasuConversationState.IDLE -> to == VasuConversationState.LISTENING || to == VasuConversationState.PROCESSING
        VasuConversationState.LISTENING -> to == VasuConversationState.PROCESSING || to == VasuConversationState.IDLE
        VasuConversationState.PROCESSING -> to == VasuConversationState.EXECUTING ||
            to == VasuConversationState.WAITING_FOR_FOLLOW_UP ||
            to == VasuConversationState.FAILED
        VasuConversationState.EXECUTING -> to == VasuConversationState.COMPLETED ||
            to == VasuConversationState.FAILED ||
            to == VasuConversationState.WAITING_FOR_FOLLOW_UP
        VasuConversationState.WAITING_FOR_FOLLOW_UP -> to == VasuConversationState.LISTENING ||
            to == VasuConversationState.PROCESSING ||
            to == VasuConversationState.IDLE
        VasuConversationState.COMPLETED -> to == VasuConversationState.LISTENING ||
            to == VasuConversationState.PROCESSING ||
            to == VasuConversationState.IDLE
        VasuConversationState.FAILED -> to == VasuConversationState.LISTENING ||
            to == VasuConversationState.PROCESSING ||
            to == VasuConversationState.IDLE
    }
}
