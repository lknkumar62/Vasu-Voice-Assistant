package com.vasu.assistant.accessibility

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class ScreenInteractionManager(
    private val service: VasuAccessibilityService
) {
    companion object {
        private const val TAG = "ScreenInteractionManager"
    }

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                Log.i(TAG, "Opened app: $packageName")
                true
            } else {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(playIntent)
                Log.i(TAG, "App not found, opened Play Store for: $packageName")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageName", e)
            false
        }
    }

    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return try {
            val result = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            if (!result) {
                node.parent?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) ?: false
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform click", e)
            false
        }
    }

    fun performScrollForward(node: AccessibilityNodeInfo?): Boolean {
        return node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }

    fun performScrollBackward(node: AccessibilityNodeInfo?): Boolean {
        return node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
    }

    fun performBack(): Boolean {
        return service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun performHome(): Boolean {
        return service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun performRecents(): Boolean {
        return service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null || !node.isEditable) return false
        val args = android.os.Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
