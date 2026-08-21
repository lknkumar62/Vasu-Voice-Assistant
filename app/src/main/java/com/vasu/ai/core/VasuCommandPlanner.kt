package com.vasu.ai.core

/** Converts common Hindi/Hinglish/English utterances into executable VASU actions. */
class VasuCommandPlanner {

    fun plan(command: String, appPackageResolver: (String) -> String?): List<VasuAction>? {
        val normalized = command.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null

        if (normalized == "back" || normalized.contains("go back") || normalized.contains("wapas ja")) return listOf(VasuAction.Back)
        if (normalized == "home" || normalized.contains("home screen") || normalized.contains("home ja")) return listOf(VasuAction.Home)
        if (normalized.contains("recent apps") || normalized.contains("recents")) return listOf(VasuAction.Recents)
        if (normalized.contains("notification shade") || normalized.contains("notifications kholo") || normalized.contains("notification kholo")) return listOf(VasuAction.Notifications)
        if (normalized.contains("lock screen") || normalized.contains("phone lock") || normalized.contains("screen lock")) return listOf(VasuAction.LockScreen)
        if (normalized.contains("take screenshot") || normalized.contains("screenshot lo") || normalized.contains("screenshot le")) return listOf(VasuAction.TakeScreenshot)

        if (normalized.contains("scroll down") || normalized.contains("neeche scroll") || normalized.contains("niche scroll")) return listOf(VasuAction.Scroll(VasuAction.Direction.DOWN))
        if (normalized.contains("scroll up") || normalized.contains("upar scroll")) return listOf(VasuAction.Scroll(VasuAction.Direction.UP))
        if (normalized.contains("swipe left") || normalized.contains("baaye swipe")) return listOf(VasuAction.Swipe(VasuAction.Direction.LEFT))
        if (normalized.contains("swipe right") || normalized.contains("daaye swipe")) return listOf(VasuAction.Swipe(VasuAction.Direction.RIGHT))
        if (normalized.contains("swipe up") || normalized.contains("upar swipe")) return listOf(VasuAction.Swipe(VasuAction.Direction.UP))
        if (normalized.contains("swipe down") || normalized.contains("neeche swipe")) return listOf(VasuAction.Swipe(VasuAction.Direction.DOWN))

        val flashlightOn = (normalized.contains("flashlight") || normalized.contains("torch") || normalized.contains("tarch")) && (normalized.contains("on") || normalized.contains("chala") || normalized.contains("jala"))
        val flashlightOff = (normalized.contains("flashlight") || normalized.contains("torch") || normalized.contains("tarch")) && (normalized.contains("off") || normalized.contains("band"))
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

        val callMatch = Regex("(?:call|phone karo|call karo)\\s+(.+)").find(normalized)
        if (callMatch != null) return listOf(VasuAction.CallContact(callMatch.groupValues[1].trim()))

        val smsMatch = Regex("(?:send sms|sms bhejo|message bhejo|text bhejo)\\s+(?:to\\s+)?(.+?)\\s+(?:message|bolo|likho|that)\\s+(.+)").find(normalized)
        if (smsMatch != null) return listOf(VasuAction.SendSms(smsMatch.groupValues[1].trim(), smsMatch.groupValues[2].trim()))

        val chainedOpenClick = Regex("(?:open|launch|khol|kholo|chalao)\\s+(.+?)\\s+(?:and|then|phir|aur)\\s+(?:click|tap|dabao|press)\\s+(.+)").find(normalized)
        if (chainedOpenClick != null) {
            val target = chainedOpenClick.groupValues[1].trim().removeSuffix(" app").trim()
            val packageName = appPackageResolver(target) ?: return null
            return listOf(VasuAction.OpenApp(packageName), VasuAction.ClickText(chainedOpenClick.groupValues[2].trim()))
        }

        val chainedOpenType = Regex("(?:open|launch|khol|kholo|chalao)\\s+(.+?)\\s+(?:and|then|phir|aur)\\s+(?:type|likho|write)\\s+(.+)").find(normalized)
        if (chainedOpenType != null) {
            val target = chainedOpenType.groupValues[1].trim().removeSuffix(" app").trim()
            val packageName = appPackageResolver(target) ?: return null
            return listOf(VasuAction.OpenApp(packageName), VasuAction.TypeText(chainedOpenType.groupValues[2].trim()))
        }

        val openMatch = Regex("(?:open|launch|khol|kholo|chalao)\\s+(.+)").find(normalized)
        if (openMatch != null) {
            val target = openMatch.groupValues[1].trim().removeSuffix(" app").trim()
            val packageName = appPackageResolver(target) ?: return null
            return listOf(VasuAction.OpenApp(packageName))
        }

        val longClickMatch = Regex("(?:long click|long press|hold|dabakar rakho)\\s+(.+)").find(normalized)
        if (longClickMatch != null) return listOf(VasuAction.LongClickText(longClickMatch.groupValues[1].trim()))

        val clickDescriptionMatch = Regex("(?:click icon|tap icon|press icon)\\s+(.+)").find(normalized)
        if (clickDescriptionMatch != null) return listOf(VasuAction.ClickDescription(clickDescriptionMatch.groupValues[1].trim()))

        val clickMatch = Regex("(?:click|tap|dabao|dabao on|press)\\s+(.+)").find(normalized)
        if (clickMatch != null) return listOf(VasuAction.ClickText(clickMatch.groupValues[1].trim()))

        val typeMatch = Regex("(?:type|likho|write)\\s+(.+)").find(normalized)
        if (typeMatch != null) return listOf(VasuAction.TypeText(typeMatch.groupValues[1].trim()))

        return null
    }
}
