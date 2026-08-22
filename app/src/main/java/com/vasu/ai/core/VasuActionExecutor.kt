package com.vasu.ai.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService
import com.vasu.ai.device.VasuCommunicationController
import com.vasu.ai.device.VasuDeviceController

class VasuActionExecutor(
    private val context: Context,
    private val clarificationManager: VasuClarificationManager = VasuClarificationManager()
) {
    private val device = VasuDeviceController(context)
    private val communication = VasuCommunicationController(context)
    private val elementRecovery = VasuElementRecovery()
    private val elementResolver = VasuElementResolver()
    private val searchFieldDetector = VasuSearchFieldDetector()
    private val clarificationTrigger = VasuClarificationTrigger(clarificationManager)

    @Volatile
    var blockedByClarification: Boolean = false
        private set

    fun execute(action: VasuAction, originalCommand: String? = null): Boolean {
        blockedByClarification = false
        return when (action) {
            is VasuAction.OpenApp -> openApp(action.packageName)
            is VasuAction.ClickText -> executeClickTextWithResolution(action.text, originalCommand)
            is VasuAction.LongClickText -> executeLongClickTextWithResolution(action.text, originalCommand)
            is VasuAction.ClickDescription -> executeClickDescriptionWithResolution(action.description, originalCommand)
            is VasuAction.ClickViewId -> executeClickViewIdWithResolution(action.viewId, originalCommand)
            is VasuAction.TypeText -> executeTypeText(action.text)
            VasuAction.ClearText -> executeClearTextWithRecovery()
            is VasuAction.Wait -> {
                val delay = action.milliseconds.coerceIn(50L, 1500L)
                SystemClock.sleep(delay)
                true
            }
            VasuAction.PressEnter -> pressEnter()
            is VasuAction.Scroll -> {
                val service = VasuAccessibilityService.instance ?: return false
                when (action.direction) {
                    VasuAction.Direction.UP -> service.scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    VasuAction.Direction.DOWN -> service.scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    VasuAction.Direction.LEFT, VasuAction.Direction.RIGHT -> swipe(service, action.direction)
                }
            }
            is VasuAction.Swipe -> VasuAccessibilityService.instance?.let { swipe(it, action.direction) } ?: false
            is VasuAction.Volume -> when (action.direction) {
                VasuAction.VolumeDirection.UP -> device.volumeUp()
                VasuAction.VolumeDirection.DOWN -> device.volumeDown()
            }
            is VasuAction.Flashlight -> device.setFlashlight(action.enabled)
            is VasuAction.CallContact -> communication.callContact(action.name)
            is VasuAction.SendSms -> communication.sendSms(action.name, action.message)
            VasuAction.Mute -> device.mute()
            VasuAction.OpenWifiSettings -> device.openWifiSettings()
            VasuAction.OpenBluetoothSettings -> device.openBluetoothSettings()
            VasuAction.OpenBrightnessSettings -> device.openBrightnessSettings()
            VasuAction.OpenDndSettings -> device.openDndSettings()
            VasuAction.OpenAirplaneModeSettings -> device.openAirplaneModeSettings()
            VasuAction.OpenBatterySaverSettings -> device.openBatterySaverSettings()
            VasuAction.OpenLocationSettings -> device.openLocationSettings()
            VasuAction.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            VasuAction.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            VasuAction.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            VasuAction.Notifications -> if (Build.VERSION.SDK_INT >= 29) global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) else false
            VasuAction.LockScreen -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else false
            VasuAction.TakeScreenshot -> if (Build.VERSION.SDK_INT >= 30) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) else false
        }
    }

    private fun blockForAmbiguity(originalCommand: String?): Boolean {
        clarificationTrigger.requestForAmbiguousElement(originalCommand.orEmpty())
        blockedByClarification = true
        println("VASU_ACTION_BLOCKED reason=AMBIGUOUS_TARGET")
        println("VASU_WORKFLOW_PAUSED reason=AMBIGUOUS_UI")
        return false
    }

    private fun executeClickTextWithResolution(text: String, originalCommand: String?): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var resolution = elementResolver.resolveText(text)
        if (!resolution.found) {
            val recovery = elementRecovery.waitForText(text)
            println("VASU_ELEMENT_RECOVERY type=TEXT query=$text found=${recovery.found} attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}")
            if (!recovery.found) return false
            resolution = elementResolver.resolveText(text)
        }
        if (resolution.ambiguous) return blockForAmbiguity(originalCommand)
        val candidate = resolution.candidate ?: return false
        println("VASU_ELEMENT_SELECTED type=TEXT query=$text score=${resolution.bestScore} class=${candidate.className} bounds=${candidate.bounds}")
        val node = candidate.node
        return try { service.click(node) } finally { node.recycle() }
    }

    private fun executeLongClickTextWithResolution(text: String, originalCommand: String?): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var resolution = elementResolver.resolveText(text)
        if (!resolution.found) {
            val recovery = elementRecovery.waitForText(text)
            if (!recovery.found) return false
            resolution = elementResolver.resolveText(text)
        }
        if (resolution.ambiguous) return blockForAmbiguity(originalCommand)
        val candidate = resolution.candidate ?: return false
        println("VASU_ELEMENT_SELECTED type=LONG_CLICK_TEXT query=$text score=${resolution.bestScore} class=${candidate.className}")
        val node = candidate.node
        return try { service.longClick(node) } finally { node.recycle() }
    }

    private fun executeClickDescriptionWithResolution(description: String, originalCommand: String?): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var resolution = elementResolver.resolveContentDescription(description)
        if (!resolution.found) {
            val recovery = elementRecovery.waitForContentDescription(description)
            println("VASU_ELEMENT_RECOVERY type=CONTENT_DESCRIPTION query=$description found=${recovery.found} attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}")
            if (!recovery.found) return service.findByText(description)?.let(service::click) == true
            resolution = elementResolver.resolveContentDescription(description)
        }
        if (resolution.ambiguous) return blockForAmbiguity(originalCommand)
        val candidate = resolution.candidate ?: return service.findByText(description)?.let(service::click) == true
        println("VASU_ELEMENT_SELECTED type=CONTENT_DESCRIPTION query=$description score=${resolution.bestScore} class=${candidate.className}")
        val node = candidate.node
        return try { service.click(node) } finally { node.recycle() }
    }

    private fun executeClickViewIdWithResolution(viewId: String, originalCommand: String?): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var resolution = elementResolver.resolveViewId(viewId)
        if (!resolution.found) {
            val recovery = elementRecovery.waitForViewId(viewId)
            println("VASU_ELEMENT_RECOVERY type=VIEW_ID query=$viewId found=${recovery.found} attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}")
            if (!recovery.found) return false
            resolution = elementResolver.resolveViewId(viewId)
        }
        if (resolution.ambiguous) return blockForAmbiguity(originalCommand)
        val candidate = resolution.candidate ?: return false
        println("VASU_ELEMENT_SELECTED type=VIEW_ID query=$viewId score=${resolution.bestScore} class=${candidate.className}")
        val node = candidate.node
        return try { service.click(node) } finally { node.recycle() }
    }

    private fun executeTypeText(text: String): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var detection = searchFieldDetector.detect()
        if (!detection.found) {
            println("VASU_SEARCH_FIELD initial_detection_failed reason=${detection.reason}")
            Thread.sleep(150L)
            detection = searchFieldDetector.detect()
        }
        if (!detection.found) {
            println("VASU_SEARCH_FIELD recovery_detection_failed reason=${detection.reason}")
            return false
        }

        val candidate = detection.candidate ?: return false
        println(
            "VASU_SEARCH_FIELD_SELECTED " +
                "score=${candidate.score} hint=${candidate.hint} " +
                "description=${candidate.contentDescription} viewId=${candidate.viewId} " +
                "focused=${candidate.focused}"
        )

        val node = candidate.node
        return try {
            if (!node.isFocused && !node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false
            Thread.sleep(80L)
            service.setText(node, text)
        } finally {
            node.recycle()
        }
    }

    private fun executeClearTextWithRecovery(): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var editable = service.focusedEditable() ?: findEditable(service.root() ?: return false)
        if (editable == null) {
            val recovery = elementRecovery.waitForEditable()
            println("VASU_ELEMENT_RECOVERY type=EDITABLE query=clear_text found=${recovery.found} attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}")
            if (!recovery.found) return false
            editable = service.focusedEditable() ?: findEditable(service.root() ?: return false)
        }
        if (editable == null || editable.isPassword || !editable.isEnabled || !editable.isVisibleToUser) return false
        if (!editable.isFocused && !editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false
        return service.setText(editable, "")
    }

    private fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val service = VasuAccessibilityService.instance ?: return false
        val focused = service.focusedEditable() ?: findEditable(service.root() ?: return false) ?: return false
        if (!focused.isEditable || focused.isPassword || !focused.isEnabled || !focused.isVisibleToUser) return false
        return focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    private fun global(action: Int): Boolean = VasuAccessibilityService.instance?.performGlobalAction(action) == true

    private fun swipe(service: VasuAccessibilityService, direction: VasuAction.Direction): Boolean {
        val metrics = context.resources.displayMetrics
        val cx = metrics.widthPixels * 0.5f
        val cy = metrics.heightPixels * 0.5f
        val dx = metrics.widthPixels * 0.32f
        val dy = metrics.heightPixels * 0.32f
        return when (direction) {
            VasuAction.Direction.UP -> service.swipe(cx, cy + dy, cx, cy - dy)
            VasuAction.Direction.DOWN -> service.swipe(cx, cy - dy, cx, cy + dy)
            VasuAction.Direction.LEFT -> service.swipe(cx + dx, cy, cx - dx, cy)
            VasuAction.Direction.RIGHT -> service.swipe(cx - dx, cy, cx + dx, cy)
        }
    }

    private fun openApp(packageName: String): Boolean = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        true
    }.getOrDefault(false)

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled && node.isVisibleToUser && !node.isPassword) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditable(child)?.let { return it }
        }
        return null
    }
}
