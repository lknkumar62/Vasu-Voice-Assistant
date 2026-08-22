package com.vasu.ai.core

import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService
import java.security.MessageDigest

/**
 * Detects whether the currently visible accessibility UI has changed.
 *
 * This is intentionally lightweight:
 * - no screenshots
 * - no OCR
 * - no Gemini call
 * - no arbitrary action retry
 *
 * It creates a deterministic fingerprint from the current accessibility tree.
 */
class VasuScreenTransitionDetector {

    data class ScreenSnapshot(
        val packageName: String?,
        val fingerprint: String,
        val nodeCount: Int,
        val timestampMs: Long
    )

    fun capture(): ScreenSnapshot? {
        val service = VasuAccessibilityService.instance ?: return null
        val root = service.root() ?: return null

        val builder = StringBuilder()
        var nodeCount = 0

        appendNodeFingerprint(
            node = root,
            builder = builder,
            counter = { nodeCount++ }
        )

        return ScreenSnapshot(
            packageName = service.foregroundPackage(),
            fingerprint = sha256(builder.toString()),
            nodeCount = nodeCount,
            timestampMs = System.currentTimeMillis()
        )
    }

    fun hasChanged(
        before: ScreenSnapshot?,
        after: ScreenSnapshot?
    ): Boolean {
        if (before == null || after == null) {
            return false
        }

        if (before.packageName != after.packageName) {
            return true
        }

        if (before.fingerprint != after.fingerprint) {
            return true
        }

        return false
    }

    private fun appendNodeFingerprint(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        counter: () -> Unit
    ) {
        counter()

        builder.append(node.className?.toString().orEmpty())
            .append('|')
            .append(node.viewIdResourceName.orEmpty())
            .append('|')
            .append(node.text?.toString().orEmpty())
            .append('|')
            .append(node.contentDescription?.toString().orEmpty())
            .append('|')
            .append(node.isClickable)
            .append('|')
            .append(node.isLongClickable)
            .append('|')
            .append(node.isEditable)
            .append('|')
            .append(node.isEnabled)
            .append('|')
            .append(node.isVisibleToUser)
            .append('|')
            .append(node.childCount)
            .append(';')

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue

            appendNodeFingerprint(
                node = child,
                builder = builder,
                counter = counter
            )

            child.recycle()
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }
}
