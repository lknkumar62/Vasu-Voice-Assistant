package com.vasu.ai.core

import java.util.UUID

class VasuConfirmationManager(
    private val policy: VasuConfirmationPolicy =
        VasuConfirmationPolicy()
) {

    private var request: VasuConfirmationRequest? = null
    private var state: VasuConfirmationState =
        VasuConfirmationState.NONE

    @Synchronized
    fun requestConfirmation(
        actionType: VasuSecurityActionType,
        description: String,
        now: Long = System.currentTimeMillis()
    ): VasuConfirmationRequest? {

        if (!policy.requiresConfirmation(actionType)) {
            return null
        }

        val safeDescription = description
            .trim()
            .take(MAX_DESCRIPTION_LENGTH)

        if (safeDescription.isBlank()) {
            return null
        }

        val newRequest = VasuConfirmationRequest(
            id = UUID.randomUUID().toString(),
            actionType = actionType,
            description = safeDescription,
            createdAt = now,
            expiresAt = policy.expirationTime(now)
        )

        request = newRequest
        state = VasuConfirmationState.PENDING

        return newRequest
    }

    @Synchronized
    fun confirm(
        id: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val current = request ?: return false

        if (state != VasuConfirmationState.PENDING) {
            return false
        }

        if (current.id != id) {
            return false
        }

        if (now >= current.expiresAt) {
            state = VasuConfirmationState.EXPIRED
            request = null
            return false
        }

        state = VasuConfirmationState.CONFIRMED
        request = null
        return true
    }

    @Synchronized
    fun cancel(id: String): Boolean {
        val current = request ?: return false

        if (state != VasuConfirmationState.PENDING) {
            return false
        }

        if (current.id != id) {
            return false
        }

        state = VasuConfirmationState.CANCELLED
        request = null
        return true
    }

    @Synchronized
    fun getState(): VasuConfirmationState = state

    @Synchronized
    fun getPendingRequest(): VasuConfirmationRequest? = request

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 500
    }
}
