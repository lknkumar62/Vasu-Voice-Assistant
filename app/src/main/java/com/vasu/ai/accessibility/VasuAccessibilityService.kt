package com.vasu.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Single UI automation engine for VASU; Android permissions and target-app restrictions still apply. */
class VasuAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: VasuAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun root(): AccessibilityNodeInfo? = rootInActiveWindow
    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    fun findByText(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val currentRoot = rootInActiveWindow ?: return null
        val nodes = currentRoot.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { node ->
            val value = node.text?.toString() ?: node.contentDescription?.toString() ?: return@firstOrNull false
            if (exact) value.equals(text, ignoreCase = true) else value.contains(text, ignoreCase = true)
        }
    }

    fun findByContentDescription(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val currentRoot = rootInActiveWindow ?: return null
        return findDescriptionRecursive(currentRoot, text, exact)
    }

    private fun findDescriptionRecursive(node: AccessibilityNodeInfo, text: String, exact: Boolean): AccessibilityNodeInfo? {
        val value = node.contentDescription?.toString().orEmpty()
        if (value.isNotBlank() && if (exact) value.equals(text, true) else value.contains(text, true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findDescriptionRecursive(child, text, exact)?.let { return it }
        }
        return null
    }

    fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent = parent.parent
        }
        return false
    }

    fun longClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isLongClickable) return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        var parent = node.parent
        while (parent != null) {
            if (parent.isLongClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            parent = parent.parent
        }
        return false
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable || !node.isEnabled || !node.isVisibleToUser) return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(directionAction: Int): Boolean {
        val currentRoot = rootInActiveWindow ?: return false
        val target = findScrollable(currentRoot) ?: return false
        return target.performAction(directionAction)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 350L): Boolean {
        if (!serviceInfo.capabilities.and(android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES).equals(android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES)) return false
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(100L, 2000L)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun describeScreen(maxItems: Int = 100): String {
        val root = rootInActiveWindow ?: return "No readable foreground window."
        val out = StringBuilder()
        out.append("package=").append(foregroundPackage() ?: "unknown").append('\n')
        var count = 0
        fun visit(node: AccessibilityNodeInfo) {
            if (count >= maxItems) return
            val text = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val value = when {
                text.isNotBlank() && description.isNotBlank() -> "$text | $description"
                text.isNotBlank() -> text
                description.isNotBlank() -> description
                else -> ""
            }
            if (value.isNotBlank()) {
                out.append("- ").append(value.take(200)).append('\n')
                count++
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
            }
        }
        visit(root)
        return out.toString()
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        }
        return null
    }
}
