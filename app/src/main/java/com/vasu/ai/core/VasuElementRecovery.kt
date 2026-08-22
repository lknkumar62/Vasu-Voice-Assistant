package com.vasu.ai.core

import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService

/**
 * Controlled recovery for accessibility targets that are temporarily
 * unavailable because the UI tree has not settled yet.
 *
 * IMPORTANT: This class only waits and re-resolves targets. It does NOT
 * execute the action, so it cannot duplicate side effects.
 */
class VasuElementRecovery {

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 1200L
        private const val POLL_INTERVAL_MS = 150L
    }

    enum class TargetType {
        TEXT,
        CONTENT_DESCRIPTION,
        VIEW_ID,
        EDITABLE
    }

    data class RecoveryResult(
        val found: Boolean,
        val targetType: TargetType,
        val query: String,
        val attempts: Int,
        val elapsedMs: Long,
        val reason: String
    )

    fun waitForText(
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): RecoveryResult = waitForTarget(
        targetType = TargetType.TEXT,
        query = text,
        timeoutMs = timeoutMs
    ) { VasuAccessibilityService.instance?.findByText(text) }

    fun waitForContentDescription(
        description: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): RecoveryResult = waitForTarget(
        targetType = TargetType.CONTENT_DESCRIPTION,
        query = description,
        timeoutMs = timeoutMs
    ) { VasuAccessibilityService.instance?.findByContentDescription(description) }

    fun waitForViewId(
        viewId: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): RecoveryResult = waitForTarget(
        targetType = TargetType.VIEW_ID,
        query = viewId,
        timeoutMs = timeoutMs
    ) { VasuAccessibilityService.instance?.findByViewId(viewId) }

    fun waitForEditable(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): RecoveryResult = waitForTarget(
        targetType = TargetType.EDITABLE,
        query = "editable",
        timeoutMs = timeoutMs
    ) {
        val service = VasuAccessibilityService.instance ?: return@waitForTarget null
        service.focusedEditable() ?: findEditable(service.root())
    }

    private fun waitForTarget(
        targetType: TargetType,
        query: String,
        timeoutMs: Long,
        finder: () -> AccessibilityNodeInfo?
    ): RecoveryResult {
        val safeTimeout = timeoutMs.coerceIn(0L, DEFAULT_TIMEOUT_MS)
        val start = System.currentTimeMillis()
        var attempts = 0

        while (true) {
            attempts++
            val node = runCatching { finder() }.getOrNull()
            if (node != null) {
                return RecoveryResult(
                    found = true,
                    targetType = targetType,
                    query = query,
                    attempts = attempts,
                    elapsedMs = System.currentTimeMillis() - start,
                    reason = "target_found"
                )
            }

            val elapsed = System.currentTimeMillis() - start
            if (elapsed >= safeTimeout) break

            Thread.sleep(POLL_INTERVAL_MS.coerceAtMost(safeTimeout - elapsed))
        }

        return RecoveryResult(
            found = false,
            targetType = targetType,
            query = query,
            attempts = attempts,
            elapsedMs = System.currentTimeMillis() - start,
            reason = "target_not_found_after_recovery_window"
        )
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isEnabled && node.isVisibleToUser && !node.isPassword) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val result = findEditable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }
}
