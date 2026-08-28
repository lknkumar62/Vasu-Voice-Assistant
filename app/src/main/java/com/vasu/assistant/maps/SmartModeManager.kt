package com.vasu.assistant.maps

import android.content.Context
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentMode: SmartMode = SmartMode.NORMAL
    private val modeConfigs = mutableMapOf<SmartMode, ModeConfig>()

    enum class SmartMode { NORMAL, DRIVING, SLEEP, WORK, GAMING, CUSTOM }

    data class ModeConfig(
        val name: String,
        val autoReply: Boolean = false,
        val autoReplyMessage: String = "",
        val dndEnabled: Boolean = false,
        val flashlightOn: Boolean = false,
        val volumeLevel: Int = -1,
        val bluetoothAutoConnect: Boolean = false,
        val notificationsMuted: Boolean = false,
        val wakeWordActive: Boolean = true
    )

    init {
        modeConfigs[SmartMode.NORMAL] = ModeConfig(name = "Normal", wakeWordActive = true)
        modeConfigs[SmartMode.DRIVING] = ModeConfig(
            name = "Driving", autoReply = true, autoReplyMessage = "I'm driving right now. Will respond later.",
            dndEnabled = true, bluetoothAutoConnect = true, wakeWordActive = true
        )
        modeConfigs[SmartMode.SLEEP] = ModeConfig(
            name = "Sleep", dndEnabled = true, flashlightOn = false,
            volumeLevel = 0, notificationsMuted = true, wakeWordActive = false
        )
        modeConfigs[SmartMode.WORK] = ModeConfig(
            name = "Work", dndEnabled = false, notificationsMuted = false,
            wakeWordActive = true, autoReply = true, autoReplyMessage = "In a meeting. Will get back to you."
        )
        modeConfigs[SmartMode.GAMING] = ModeConfig(
            name = "Gaming", dndEnabled = true, notificationsMuted = true,
            wakeWordActive = false, volumeLevel = 80
        )
    }

    fun setMode(mode: SmartMode): ActionResult {
        currentMode = mode
        val config = modeConfigs[mode] ?: ModeConfig(name = mode.name)
        return ActionResult.success("mode", "Smart mode set to ${config.name}", mapOf(
            "mode" to mode.name, "config" to mapOf(
                "autoReply" to config.autoReply, "dnd" to config.dndEnabled,
                "wakeWord" to config.wakeWordActive, "volume" to config.volumeLevel
            )
        ))
    }

    fun getCurrentMode(): ActionResult {
        val config = modeConfigs[currentMode]
        return ActionResult.success("mode", "Current: ${config?.name ?: currentMode.name}", mapOf("mode" to currentMode.name))
    }

    fun createCustomMode(name: String, autoReply: Boolean = false, dnd: Boolean = false): ActionResult {
        val config = ModeConfig(name = name, autoReply = autoReply, dndEnabled = dnd)
        modeConfigs[SmartMode.CUSTOM] = config
        return ActionResult.success("custom_mode", "Custom mode '$name' created")
    }

    fun getAvailableModes(): ActionResult {
        val modes = modeConfigs.map { (k, v) -> mapOf("name" to k.name, "label" to v.name) }
        return ActionResult.success("modes", "Available modes", mapOf("modes" to modes))
    }

    fun autoDetectMode(): ActionResult {
        val hour = LocalTime.now().hour
        val mode = when {
            hour in 22..23 || hour in 0..5 -> SmartMode.SLEEP
            hour in 9..17 -> SmartMode.WORK
            else -> SmartMode.NORMAL
        }
        return setMode(mode)
    }
}
