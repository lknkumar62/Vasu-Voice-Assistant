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

        expireIfNeeded(now)

        val active = request
        if (active != null &&
            (state == VasuConfirmationState.PENDING ||
                state == VasuConfirmationState.CONFIRMED)
        ) {
            return if (active.actionType == actionType) {
                active
            } else {
                null
            }
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
        expireIfNeeded(now)

        val current = request ?: return false

        if (state != VasuConfirmationState.PENDING) {
            return false
        }

        if (current.id != id) {
            return false
        }

        state = VasuConfirmationState.CONFIRMED
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
    fun consumeConfirmed(
        id: String,
        actionType: VasuSecurityActionType,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        expireIfNeeded(now)

        val current = request ?: return false

        if (state != VasuConfirmationState.CONFIRMED) {
            return false
        }

        if (current.id != id) {
            return false
        }

        if (current.actionType != actionType) {
            return false
        }

        request = null
        state = VasuConfirmationState.NONE
        return true
    }

    @Synchronized
    fun expireIfNeeded(
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val current = request ?: return false

        if (
            (state == VasuConfirmationState.PENDING ||
                state == VasuConfirmationState.CONFIRMED) &&
            now >= current.expiresAt
        ) {
            state = VasuConfirmationState.EXPIRED
            request = null
            return true
        }

        return false
    }

    @Synchronized
    fun getState(): VasuConfirmationState {
        expireIfNeeded()
        return state
    }

    @Synchronized
    fun getPendingRequest(): VasuConfirmationRequest? {
        expireIfNeeded()
        return request
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 500
    }
}
