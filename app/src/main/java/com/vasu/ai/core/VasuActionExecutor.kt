package com.vasu.ai.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.vasu.ai.accessibility.VasuAccessibilityService

class VasuActionExecutor(private val context: Context) {

    fun execute(action: VasuAction): Boolean = when (action) {
        is VasuAction.OpenApp -> openApp(action.packageName)
        is VasuAction.ClickText -> VasuAccessibilityService.instance?.findByText(action.text)?.let {
            VasuAccessibilityService.instance?.click(it) == true
        } == true
        is VasuAction.TypeText -> {
            val service = VasuAccessibilityService.instance ?: return false
            val root = service.root() ?: return false
            val editable = findEditable(root) ?: return false
            service.setText(editable, action.text)
        }
        is VasuAction.Scroll -> {
            val service = VasuAccessibilityService.instance ?: return false
            val command = when (action.direction) {
                VasuAction.Direction.UP -> AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
                VasuAction.Direction.DOWN -> AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
                VasuAction.Direction.LEFT -> AccessibilityNodeInfoCompat.ACTION_SCROLL_LEFT
                VasuAction.Direction.RIGHT -> AccessibilityNodeInfoCompat.ACTION_SCROLL_RIGHT
            }
            service.scroll(command)
        }
        VasuAction.Back -> VasuAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
        VasuAction.Home -> VasuAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
        VasuAction.Recents -> VasuAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true
    }

    private fun openApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun findEditable(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditable(child)
            if (result != null) return result
        }
        return null
    }
}

private object AccessibilityNodeInfoCompat {
    const val ACTION_SCROLL_FORWARD = android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
    const val ACTION_SCROLL_BACKWARD = android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
    // Android node scrolling is primarily vertical; horizontal actions require a gesture fallback.
    const val ACTION_SCROLL_LEFT = 16908355
    const val ACTION_SCROLL_RIGHT = 16908356
}
