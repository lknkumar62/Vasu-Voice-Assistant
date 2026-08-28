package com.vasu.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.assistant.core.automation.ActionResult

/**
 * ScreenInteractionManager - High-level screen interaction manager.
 *
 * Provides methods for complex multi-step interactions like:
 * - Opening an app and performing actions
 * - Finding and clicking elements
 * - Filling forms
 * - Navigation flows
 */
class ScreenInteractionManager(
    private val service: AccessibilityService
) {
    private val nodeFinder = AccessibilityNodeFinder()
    private val actions = AccessibilityActions(service)
    private val screenReader = ScreenReader()

    /**
     * Open an app by package name
     */
    fun openApp(packageName: String): ActionResult {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                ActionResult.success("open_app", "Opened $packageName")
            } else {
                ActionResult.error("open_app", "App not found: $packageName", "Package not found")
            }
        } catch (e: Exception) {
            ActionResult.error("open_app", "Failed to open $packageName", e.message ?: "Unknown error")
        }
    }

    /**
     * Find and click element with retry
     */
    fun findAndClick(text: String, maxRetries: Int = 3): ActionResult {
        repeat(maxRetries) { attempt ->
            val result = actions.clickByText(text)
            if (result.success) return result

            if (attempt < maxRetries - 1) {
                Thread.sleep(1000) // Wait before retry
            }
        }
        return ActionResult.error("find_click", "Could not find: $text", "Element not found after $maxRetries retries")
    }

    /**
     * Fill text field
     */
    fun fillTextField(label: String, text: String): ActionResult {
        return actions.typeTextByLabel(label, text)
    }

    /**
     * Get current screen state
     */
    fun getScreenState(): ScreenContent {
        return screenReader.readScreen(service.rootInActiveWindow)
    }

    /**
     * Wait for element to appear
     */
    fun waitForElement(text: String, timeoutMs: Long = 10000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val node = nodeFinder.findNodeContainingText(service.rootInActiveWindow, text)
            if (node != null) return true
            Thread.sleep(500)
        }
        return false
    }

    /**
     * Check if app is in foreground
     */
    fun isAppInForeground(packageName: String): Boolean {
        return try {
            val rootNode = service.rootInActiveWindow
            val nodeInfo = nodeFinder.findByViewId(rootNode, "android:id/content")
            nodeInfo?.packageName?.toString() == packageName
        } catch (e: Exception) {
            false
        }
    }
}
