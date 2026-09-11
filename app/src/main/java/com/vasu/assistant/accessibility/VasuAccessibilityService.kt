package com.vasu.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class VasuAccessibilityService : AccessibilityService() {

    private lateinit var nodeFinder: AccessibilityNodeFinder
    private lateinit var actions: AccessibilityActions
    private lateinit var screenReader: ScreenReader
    private lateinit var interactionManager: ScreenInteractionManager

    companion object {
        private const val TAG = "VasuAccessibility"

        private val _instance = MutableStateFlow<VasuAccessibilityService?>(null)
        val instance: StateFlow<VasuAccessibilityService?> = _instance.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        var foregroundPackage: String? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        nodeFinder = AccessibilityNodeFinder()
        actions = AccessibilityActions(this)
        screenReader = ScreenReader()
        interactionManager = ScreenInteractionManager(this)

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }

        _instance.value = this
        _isRunning.value = true

        Log.d(TAG, "VASU Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            val pkg = it.packageName?.toString()
            val ignoredPackages = listOf(
                "com.android.systemui",
                "com.vasu.assistant",
                "com.miui.home",
                "com.android.launcher",
                "com.android.inputmethod.latin"
            )

            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (pkg != null && !pkg.startsWith("inputmethod") && pkg !in ignoredPackages) {
                        foregroundPackage = pkg
                        Log.d(TAG, "Foreground app: $pkg")
                    }
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    Log.d(TAG, "View clicked: ${it.text}")
                }
                else -> { /* ignore */ }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "VASU Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _instance.value = null
        _isRunning.value = false
        Log.d(TAG, "VASU Accessibility Service destroyed")
    }

    fun getScreenContent(): ScreenContent {
        return screenReader.readScreen(rootInActiveWindow)
    }

    fun findElement(text: String): AccessibilityNodeInfo? {
        return nodeFinder.findNodeContainingText(rootInActiveWindow, text)
    }

    fun clickElement(text: String): ActionResult {
        return actions.clickByText(text)
    }

    fun typeText(label: String, text: String): ActionResult {
        return actions.typeTextByLabel(label, text)
    }

    fun scrollDown(): ActionResult {
        return actions.scrollDown()
    }

    fun scrollUp(): ActionResult {
        return actions.scrollUp()
    }

    fun pressBack(): ActionResult {
        return actions.pressBack()
    }

    fun pressHome(): ActionResult {
        return actions.pressHome()
    }

    fun openApp(packageName: String): ActionResult {
        return interactionManager.openApp(packageName)
    }

    fun readScreen(): ActionResult {
        return actions.readScreen()
    }

    fun getInteractionManager(): ScreenInteractionManager {
        return interactionManager
    }

    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        try {
            takeScreenshot(
                DISPLAY_ID_DEFAULT,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace
                        )
                        result.hardwareBuffer?.close()
                        callback(bitmap)
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed: $errorCode")
                        callback(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot error: ${e.message}")
            callback(null)
        }
    }

    fun getScreenDimensions(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    fun getVisibleWindows(): List<AccessibilityWindowInfo> {
        return try {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getNodeLabel(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        return when {
            !text.isNullOrEmpty() -> text
            !desc.isNullOrEmpty() -> desc
            else -> "(no label)"
        }
    }
}
