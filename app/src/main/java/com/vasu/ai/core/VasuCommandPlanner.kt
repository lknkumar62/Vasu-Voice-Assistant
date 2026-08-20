package com.vasu.ai.core

/**
 * Converts common Hindi/Hinglish/English utterances into the stable VASU action contract.
 * Unknown requests deliberately return null so a future cloud planner can handle them.
 */
class VasuCommandPlanner {

    fun plan(command: String, appPackageResolver: (String) -> String?): List<VasuAction>? {
        val normalized = command.trim().lowercase()
        if (normalized.isBlank()) return null

        if (normalized == "back" || normalized.contains("go back") || normalized.contains("wapas ja")) {
            return listOf(VasuAction.Back)
        }
        if (normalized == "home" || normalized.contains("home screen") || normalized.contains("home ja")) {
            return listOf(VasuAction.Home)
        }
        if (normalized.contains("recent apps") || normalized.contains("recents")) {
            return listOf(VasuAction.Recents)
        }
        if (normalized.contains("scroll down") || normalized.contains("neeche scroll") || normalized.contains("niche scroll")) {
            return listOf(VasuAction.Scroll(VasuAction.Direction.DOWN))
        }
        if (normalized.contains("scroll up") || normalized.contains("upar scroll")) {
            return listOf(VasuAction.Scroll(VasuAction.Direction.UP))
        }

        val openMatch = Regex("(?:open|launch|khol|kholo|chalao)\\s+(.+)").find(normalized)
        if (openMatch != null) {
            val packageName = appPackageResolver(openMatch.groupValues[1].trim()) ?: return null
            return listOf(VasuAction.OpenApp(packageName))
        }

        val clickMatch = Regex("(?:click|tap|dabao|dabao on|press)\\s+(.+)").find(normalized)
        if (clickMatch != null) {
            return listOf(VasuAction.ClickText(clickMatch.groupValues[1].trim()))
        }

        val typeMatch = Regex("(?:type|likho|write)\\s+(.+)").find(normalized)
        if (typeMatch != null) {
            return listOf(VasuAction.TypeText(typeMatch.groupValues[1].trim()))
        }

        return null
    }
}
