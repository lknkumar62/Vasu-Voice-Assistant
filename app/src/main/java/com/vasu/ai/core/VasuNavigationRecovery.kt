package com.vasu.ai.core

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService

/**
 * Performs bounded navigation recovery using safe Back navigation and fresh observation.
 * No sensitive action is executed or repeated here.
 */
class VasuNavigationRecovery {

    companion object {
        const val RECOVERY_TIMEOUT_MS = 1800L
        const val POLL_INTERVAL_MS = 150L
        const val MAX_BACK_PRESSES = 2
    }

    data class RecoveryResult(
        val recovered: Boolean,
        val backPresses: Int,
        val reason: String
    )

    fun recover(expectedPackage: String? = null): RecoveryResult {
        val service = VasuAccessibilityService.instance
            ?: return RecoveryResult(false, 0, "accessibility_service_unavailable")

        val start = SystemClock.uptimeMillis()
        var backPresses = 0

        while (SystemClock.uptimeMillis() - start < RECOVERY_TIMEOUT_MS) {
            val currentPackage = service.foregroundPackage()
            if ((expectedPackage.isNullOrBlank() || currentPackage == expectedPackage) &&
                isUsableScreen(service)
            ) {
                println(
                    "VASU_NAVIGATION_RECOVERY " +
                        "recovered=true backPresses=$backPresses package=$currentPackage"
                )
                return RecoveryResult(true, backPresses, "usable_screen")
            }

            if (backPresses >= MAX_BACK_PRESSES) break

            val backResult = service.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            )
            if (!backResult) break

            backPresses++
            SystemClock.sleep(POLL_INTERVAL_MS)
        }

        println(
            "VASU_NAVIGATION_RECOVERY " +
                "recovered=false backPresses=$backPresses"
        )
        return RecoveryResult(false, backPresses, "recovery_timeout")
    }

    private fun isUsableScreen(service: VasuAccessibilityService): Boolean {
        val root = service.root() ?: return false
        return try {
            root.isVisibleToUser && hasUsefulContent(root)
        } finally {
            // root() exposes the active accessibility root; use a defensive copy for recursion.
            runCatching { root.recycle() }
        }
    }

    private fun hasUsefulContent(node: AccessibilityNodeInfo): Boolean {
        if (!node.text.isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank() ||
            node.isClickable ||
            node.isEditable
        ) {
            return true
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val useful = try {
                hasUsefulContent(child)
            } finally {
                runCatching { child.recycle() }
            }
            if (useful) return true
        }
        return false
    }
}
