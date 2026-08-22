package com.vasu.ai.core

import java.util.UUID

class VasuConversationContextStore {
    companion object {
        const val DEFAULT_CONTEXT_EXPIRY_MS = 5 * 60 * 1000L
        const val MAX_TURNS = 20
    }

    private var context = VasuConversationContext(
        sessionId = UUID.randomUUID().toString(),
        state = VasuConversationState.IDLE,
        lastUpdatedMs = System.currentTimeMillis()
    )

    fun get(): VasuConversationContext {
        expireIfNeeded()
        return context
    }

    fun updateState(state: VasuConversationState) {
        context = context.copy(state = state, lastUpdatedMs = System.currentTimeMillis())
    }

    fun updateUserCommand(command: String) {
        context = context.copy(lastUserCommand = command, lastUpdatedMs = System.currentTimeMillis())
    }

    fun updateAssistantResponse(response: String?) {
        context = context.copy(lastAssistantResponse = response, lastUpdatedMs = System.currentTimeMillis())
    }

    fun updateActiveApp(appName: String?, packageName: String?) {
        context = context.copy(
            activeAppName = appName,
            activeAppPackage = packageName,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    fun updateLastAction(actionName: String?, successful: Boolean) {
        context = context.copy(
            lastSuccessfulAction = actionName,
            lastWorkflowSuccessful = successful,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    fun addTurn(turn: VasuConversationTurn) {
        context = context.copy(
            turns = (context.turns + turn).takeLast(MAX_TURNS),
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    fun clear() {
        context = VasuConversationContext(
            sessionId = UUID.randomUUID().toString(),
            state = VasuConversationState.IDLE,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    fun isExpired(): Boolean =
        System.currentTimeMillis() - context.lastUpdatedMs > DEFAULT_CONTEXT_EXPIRY_MS

    fun expireIfNeeded() {
        if (isExpired()) {
            println("VASU_CONTEXT_EXPIRED")
            clear()
        }
    }

    fun hasActiveContext(): Boolean {
        expireIfNeeded()
        return context.lastUserCommand != null ||
            context.activeAppName != null ||
            context.lastSuccessfulAction != null
    }

    fun isFollowUpCandidate(input: String): Boolean {
        val normalized = input.trim().lowercase()
        if (!hasActiveContext()) return false
        return normalized in setOf(
            "haan", "ha", "yes", "okay", "ok", "continue", "phir", "fir",
            "search karo", "isko kholo", "ye kholo", "pehla wala kholo"
        ) || normalized.startsWith("haan ") ||
            normalized.startsWith("phir ") || normalized.startsWith("fir ")
    }
}
