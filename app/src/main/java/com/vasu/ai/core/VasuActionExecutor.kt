package com.vasu.ai.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService
import com.vasu.ai.device.VasuDeviceController

class VasuActionExecutor(private val context: Context) {

    private val device = VasuDeviceController(context)

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
            when (action.direction) {
                VasuAction.Direction.UP -> service.scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                VasuAction.Direction.DOWN -> service.scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                VasuAction.Direction.LEFT, VasuAction.Direction.RIGHT -> false
            }
        }
        is VasuAction.Volume -> when (action.direction) {
            VasuAction.VolumeDirection.UP -> device.volumeUp()
            VasuAction.VolumeDirection.DOWN -> device.volumeDown()
        }
        VasuAction.Mute -> device.mute()
        VasuAction.OpenWifiSettings -> device.openWifiSettings()
        VasuAction.OpenBluetoothSettings -> device.openBluetoothSettings()
        VasuAction.OpenBrightnessSettings -> device.openBrightnessSettings()
        VasuAction.OpenDndSettings -> device.openDndSettings()
        VasuAction.OpenAirplaneModeSettings -> device.openAirplaneModeSettings()
        VasuAction.OpenBatterySaverSettings -> device.openBatterySaverSettings()
        VasuAction.OpenLocationSettings -> device.openLocationSettings()
        VasuAction.Back -> VasuAccessibilityService.instance
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
        VasuAction.Home -> VasuAccessibilityService.instance
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
        VasuAction.Recents -> VasuAccessibilityService.instance
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true
    }

    private fun openApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditable(child)
            if (result != null) return result
        }
        return null
    }
}
