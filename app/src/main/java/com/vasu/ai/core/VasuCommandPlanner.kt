package com.vasu.ai.core

/** Converts common Hindi/Hinglish/English utterances into executable VASU actions. */
class VasuCommandPlanner {

    fun plan(command: String, appPackageResolver: (String) -> String?): List<VasuAction>? {
        val normalized = command.trim().lowercase()
        if (normalized.isBlank()) return null

        if (normalized == "back" || normalized.contains("go back") || normalized.contains("wapas ja")) return listOf(VasuAction.Back)
        if (normalized == "home" || normalized.contains("home screen") || normalized.contains("home ja")) return listOf(VasuAction.Home)
        if (normalized.contains("recent apps") || normalized.contains("recents")) return listOf(VasuAction.Recents)
        if (normalized.contains("scroll down") || normalized.contains("neeche scroll") || normalized.contains("niche scroll")) return listOf(VasuAction.Scroll(VasuAction.Direction.DOWN))
        if (normalized.contains("scroll up") || normalized.contains("upar scroll")) return listOf(VasuAction.Scroll(VasuAction.Direction.UP))

        val flashlightOn = normalized.contains("flashlight") && (normalized.contains("on") || normalized.contains("chala") || normalized.contains("jala"))
        val flashlightOff = normalized.contains("flashlight") && (normalized.contains("off") || normalized.contains("band"))
        if (flashlightOn) return listOf(VasuAction.Flashlight(true))
        if (flashlightOff) return listOf(VasuAction.Flashlight(false))

        if (normalized.contains("volume up") || normalized.contains("volume badha") || normalized.contains("awaz badha")) return listOf(VasuAction.Volume(VasuAction.VolumeDirection.UP))
        if (normalized.contains("volume down") || normalized.contains("volume kam") || normalized.contains("awaz kam")) return listOf(VasuAction.Volume(VasuAction.VolumeDirection.DOWN))
        if (normalized.contains("mute") || normalized.contains("silent kar")) return listOf(VasuAction.Mute)
        if (normalized.contains("wifi") && (normalized.contains("open") || normalized.contains("settings") || normalized.contains("khol"))) return listOf(VasuAction.OpenWifiSettings)
        if (normalized.contains("bluetooth") && (normalized.contains("open") || normalized.contains("settings") || normalized.contains("khol"))) return listOf(VasuAction.OpenBluetoothSettings)
        if (normalized.contains("brightness") || normalized.contains("screen light")) return listOf(VasuAction.OpenBrightnessSettings)
        if (normalized.contains("do not disturb") || normalized.contains("dnd")) return listOf(VasuAction.OpenDndSettings)
        if (normalized.contains("airplane mode") || normalized.contains("flight mode")) return listOf(VasuAction.OpenAirplaneModeSettings)
        if (normalized.contains("battery saver") || normalized.contains("power saver")) return listOf(VasuAction.OpenBatterySaverSettings)
        if (normalized.contains("location settings") || normalized.contains("gps settings")) return listOf(VasuAction.OpenLocationSettings)

        val openMatch = Regex("(?:open|launch|khol|kholo|chalao)\\s+(.+)").find(normalized)
        if (openMatch != null) {
            val packageName = appPackageResolver(openMatch.groupValues[1].trim()) ?: return null
            return listOf(VasuAction.OpenApp(packageName))
        }

        val clickMatch = Regex("(?:click|tap|dabao|dabao on|press)\\s+(.+)").find(normalized)
        if (clickMatch != null) return listOf(VasuAction.ClickText(clickMatch.groupValues[1].trim()))

        val typeMatch = Regex("(?:type|likho|write)\\s+(.+)").find(normalized)
        if (typeMatch != null) return listOf(VasuAction.TypeText(typeMatch.groupValues[1].trim()))

        return null
    }
}
