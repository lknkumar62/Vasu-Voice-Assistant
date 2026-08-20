package com.vasu.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Single UI automation engine for VASU.
 * All actions remain subject to Android and target-app restrictions.
 */
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

    fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent = parent.parent
        }
        return false
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) return false
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(directionAction: Int): Boolean {
        val currentRoot = rootInActiveWindow ?: return false
        val target = findScrollable(currentRoot) ?: return false
        return target.performAction(directionAction)
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollable(child)
            if (result != null) return result
        }
        return null
    }
}
