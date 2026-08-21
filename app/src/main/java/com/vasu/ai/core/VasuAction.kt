package com.vasu.ai.core

/** Stable executable action contract for VASU. */
sealed interface VasuAction {
    data class OpenApp(val packageName: String) : VasuAction
    data class ClickText(val text: String) : VasuAction
    data class LongClickText(val text: String) : VasuAction
    data class ClickDescription(val description: String) : VasuAction
    data class TypeText(val text: String) : VasuAction
    data class Scroll(val direction: Direction) : VasuAction
    data class Swipe(val direction: Direction) : VasuAction
    data class Volume(val direction: VolumeDirection) : VasuAction
    data class Flashlight(val enabled: Boolean) : VasuAction
    data class CallContact(val name: String) : VasuAction
    data class SendSms(val name: String, val message: String) : VasuAction
    data object Mute : VasuAction
    data object OpenWifiSettings : VasuAction
    data object OpenBluetoothSettings : VasuAction
    data object OpenBrightnessSettings : VasuAction
    data object OpenDndSettings : VasuAction
    data object OpenAirplaneModeSettings : VasuAction
    data object OpenBatterySaverSettings : VasuAction
    data object OpenLocationSettings : VasuAction
    data object Back : VasuAction
    data object Home : VasuAction
    data object Recents : VasuAction

    enum class Direction { UP, DOWN, LEFT, RIGHT }
    enum class VolumeDirection { UP, DOWN }
}
