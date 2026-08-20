package com.vasu.ai.core

/**
 * Converts only explicitly allowed Gemini steps into the existing VasuAction contract.
 * Unsupported or malformed steps are rejected instead of being executed.
 */
class GeminiActionValidator(
    private val appResolver: VasuAppResolver
) {
    data class ValidationResult(
        val actions: List<VasuAction>,
        val rejectedCount: Int
    )

    fun validate(steps: List<GeminiStep>): ValidationResult {
        var rejected = 0
        val actions = buildList {
            for (step in steps) {
                val normalized = step.action.trim().lowercase()
                val action = when (normalized) {
                    "open_app" -> appResolver.resolve(step.target)?.let(VasuAction::OpenApp)
                    "click_text" -> step.target.takeIf { it.isNotBlank() }?.let(VasuAction::ClickText)
                    "type_text" -> step.value.takeIf { it.isNotBlank() }?.let(VasuAction::TypeText)
                    "scroll_up" -> VasuAction.Scroll(VasuAction.Direction.UP)
                    "scroll_down" -> VasuAction.Scroll(VasuAction.Direction.DOWN)
                    "back" -> VasuAction.Back
                    "home" -> VasuAction.Home
                    "recents" -> VasuAction.Recents
                    "volume_up" -> VasuAction.Volume(VasuAction.VolumeDirection.UP)
                    "volume_down" -> VasuAction.Volume(VasuAction.VolumeDirection.DOWN)
                    "mute" -> VasuAction.Mute
                    "flashlight_on" -> VasuAction.Flashlight(true)
                    "flashlight_off" -> VasuAction.Flashlight(false)
                    "open_wifi_settings" -> VasuAction.OpenWifiSettings
                    "open_bluetooth_settings" -> VasuAction.OpenBluetoothSettings
                    "open_brightness_settings" -> VasuAction.OpenBrightnessSettings
                    "open_dnd_settings" -> VasuAction.OpenDndSettings
                    "open_airplane_settings" -> VasuAction.OpenAirplaneModeSettings
                    "open_battery_saver_settings" -> VasuAction.OpenBatterySaverSettings
                    "open_location_settings" -> VasuAction.OpenLocationSettings
                    else -> null
                }
                if (action == null) rejected++ else add(action)
            }
        }
        return ValidationResult(actions, rejected)
    }
}
