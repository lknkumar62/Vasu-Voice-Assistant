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

class VasuActionExecutor(private val context: Context) {
    private val device = VasuDeviceController(context)
    private val communication = VasuCommunicationController(context)
    private val elementRecovery = VasuElementRecovery()

    fun execute(action: VasuAction): Boolean {
        return when (action) {
            is VasuAction.OpenApp -> openApp(action.packageName)
            is VasuAction.ClickText -> executeClickTextWithRecovery(action.text)
            is VasuAction.LongClickText -> executeLongClickTextWithRecovery(action.text)
            is VasuAction.ClickDescription -> executeClickDescriptionWithRecovery(action.description)
            is VasuAction.ClickViewId -> executeClickViewIdWithRecovery(action.viewId)
            is VasuAction.TypeText -> executeTypeTextWithRecovery(action.text)
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

    private fun executeClickTextWithRecovery(text: String): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var node = service.findByText(text)
        if (node == null) {
            val recovery = elementRecovery.waitForText(text)
            println(
                "VASU_ELEMENT_RECOVERY " +
                    "type=TEXT query=$text found=${recovery.found} " +
                    "attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}"
            )
            if (!recovery.found) return false
            node = service.findByText(text)
        }
        return node?.let(service::click) == true
    }

    private fun executeLongClickTextWithRecovery(text: String): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var node = service.findByText(text)
        if (node == null) {
            val recovery = elementRecovery.waitForText(text)
            println(
                "VASU_ELEMENT_RECOVERY " +
                    "type=LONG_CLICK_TEXT query=$text found=${recovery.found} " +
                    "attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}"
            )
            if (!recovery.found) return false
            node = service.findByText(text)
        }
        return node?.let(service::longClick) == true
    }

    private fun executeClickDescriptionWithRecovery(description: String): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var node = service.findByContentDescription(description) ?: service.findByText(description)
        if (node == null) {
            val recovery = elementRecovery.waitForContentDescription(description)
            println(
                "VASU_ELEMENT_RECOVERY " +
                    "type=CONTENT_DESCRIPTION query=$description found=${recovery.found} " +
                    "attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}"
            )
            if (!recovery.found) return false
            node = service.findByContentDescription(description) ?: service.findByText(description)
        }
        return node?.let(service::click) == true
    }

    private fun executeClickViewIdWithRecovery(viewId: String): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var node = service.findByViewId(viewId)
        if (node == null) {
            val recovery = elementRecovery.waitForViewId(viewId)
            println(
                "VASU_ELEMENT_RECOVERY " +
                    "type=VIEW_ID query=$viewId found=${recovery.found} " +
                    "attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}"
            )
            if (!recovery.found) return false
            node = service.findByViewId(viewId)
        }
        return node?.let(service::click) == true
    }

    private fun executeTypeTextWithRecovery(text: String): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var editable = service.focusedEditable() ?: findEditable(service.root() ?: return false)

        if (editable == null) {
            val recovery = elementRecovery.waitForEditable()
            println(
                "VASU_ELEMENT_RECOVERY " +
                    "type=EDITABLE query=type_text found=${recovery.found} " +
                    "attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}"
            )
            if (!recovery.found) return false
            editable = service.focusedEditable() ?: findEditable(service.root() ?: return false)
        }

        if (editable == null || editable.isPassword || !editable.isEnabled || !editable.isVisibleToUser) return false
        if (!editable.isFocused && !editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false
        return service.setText(editable, text)
    }

    private fun executeClearTextWithRecovery(): Boolean {
        val service = VasuAccessibilityService.instance ?: return false
        var editable = service.focusedEditable() ?: findEditable(service.root() ?: return false)

        if (editable == null) {
            val recovery = elementRecovery.waitForEditable()
            println(
                "VASU_ELEMENT_RECOVERY " +
                    "type=EDITABLE query=clear_text found=${recovery.found} " +
                    "attempts=${recovery.attempts} elapsed=${recovery.elapsedMs}"
            )
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

    private fun global(action: Int): Boolean =
        VasuAccessibilityService.instance?.performGlobalAction(action) == true

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
