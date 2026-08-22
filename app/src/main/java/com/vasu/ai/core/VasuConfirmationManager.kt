package com.vasu.ai.core

import java.util.UUID

class VasuConfirmationManager(
    private val policy: VasuConfirmationPolicy = VasuConfirmationPolicy(),
    private val lifecycle: VasuConfirmationLifecycle = VasuConfirmationLifecycle()
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
            lifecycle.onRejected(
                requestId = null,
                actionType = actionType,
                reason = "confirmation_not_required",
                now = now
            )
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
                lifecycle.onRejected(
                    requestId = active.id,
                    actionType = actionType,
                    reason = "different_active_action",
                    now = now
                )
                null
            }
        }

        val safeDescription = description
            .trim()
            .take(MAX_DESCRIPTION_LENGTH)

        if (safeDescription.isBlank()) {
            lifecycle.onRejected(
                requestId = null,
                actionType = actionType,
                reason = "blank_description",
                now = now
            )
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

        lifecycle.onRequested(
            request = newRequest,
            now = now
        )

        return newRequest
    }

    @Synchronized
    fun confirm(
        id: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        expireIfNeeded(now)

        val current = request

        if (current == null) {
            lifecycle.onRejected(
                requestId = id,
                actionType = null,
                reason = "request_not_found",
                now = now
            )
            return false
        }

        if (state != VasuConfirmationState.PENDING) {
            lifecycle.onRejected(
                requestId = id,
                actionType = current.actionType,
                reason = "request_not_pending",
                now = now
            )
            return false
        }

        if (current.id != id) {
            lifecycle.onRejected(
                requestId = id,
                actionType = current.actionType,
                reason = "request_id_mismatch",
                now = now
            )
            return false
        }

        state = VasuConfirmationState.CONFIRMED

        lifecycle.onConfirmed(
            requestId = current.id,
            actionType = current.actionType,
            now = now
        )

        return true
    }

    @Synchronized
    fun cancel(id: String): Boolean {
        val now = System.currentTimeMillis()
        val current = request

        if (current == null) {
            lifecycle.onRejected(
                requestId = id,
                actionType = null,
                reason = "request_not_found",
                now = now
            )
            return false
        }

        if (state != VasuConfirmationState.PENDING) {
            lifecycle.onRejected(
                requestId = id,
                actionType = current.actionType,
                reason = "request_not_pending",
                now = now
            )
            return false
        }

        if (current.id != id) {
            lifecycle.onRejected(
                requestId = id,
                actionType = current.actionType,
                reason = "request_id_mismatch",
                now = now
            )
            return false
        }

        state = VasuConfirmationState.CANCELLED
        request = null

        lifecycle.onCancelled(
            requestId = current.id,
            actionType = current.actionType
        )

        return true
    }

    @Synchronized
    fun consumeConfirmed(
        id: String,
        actionType: VasuSecurityActionType,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        expireIfNeeded(now)

        val current = request

        if (current == null) {
            lifecycle.onRejected(
                requestId = id,
                actionType = actionType,
                reason = "request_not_found",
                now = now
            )
            return false
        }

        if (state != VasuConfirmationState.CONFIRMED) {
            lifecycle.onRejected(
                requestId = id,
                actionType = actionType,
                reason = "request_not_confirmed",
                now = now
            )
            return false
        }

        if (current.id != id) {
            lifecycle.onRejected(
                requestId = id,
                actionType = actionType,
                reason = "request_id_mismatch",
                now = now
            )
            return false
        }

        if (current.actionType != actionType) {
            lifecycle.onRejected(
                requestId = id,
                actionType = actionType,
                reason = "action_type_mismatch",
                now = now
            )
            return false
        }

        request = null
        state = VasuConfirmationState.NONE

        lifecycle.onConsumed(
            requestId = current.id,
            actionType = current.actionType,
            now = now
        )

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
            val expiredId = current.id
            val expiredType = current.actionType

            state = VasuConfirmationState.EXPIRED
            request = null

            lifecycle.onExpired(
                requestId = expiredId,
                actionType = expiredType,
                now = now
            )

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

    fun auditSnapshot(): VasuConfirmationAuditSnapshot =
        lifecycle.snapshotData()

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 500
    }
}
