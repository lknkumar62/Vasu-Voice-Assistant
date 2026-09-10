package com.vasu.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.d(TAG, "Window changed: ${it.packageName}")
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    Log.d(TAG, "View clicked: ${it.text}")
                }
                else -> { /* ignore other event types */ }
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
}
