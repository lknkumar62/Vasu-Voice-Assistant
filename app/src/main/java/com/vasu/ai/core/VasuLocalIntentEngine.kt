package com.vasu.ai.core

class VasuLocalIntentEngine(
    private val normalizer: VasuCommandNormalizer = VasuCommandNormalizer()
) {
    fun parse(input: String): VasuLocalIntent {
        val normalized = normalizer.normalizeHindiHinglish(input)
        if (normalized.isBlank()) return VasuLocalIntent.Unknown

        if (containsAny(normalized, listOf("stop", "ruk jao", "ruk ja", "cancel", "band karo", "bas"))) return VasuLocalIntent.Stop
        if (containsAny(normalized, listOf("back", "go back", "back jao", "peeche jao", "piche jao", "wapas jao"))) return VasuLocalIntent.GoBack
        if (containsAny(normalized, listOf("home", "go home", "ghar jao", "home jao"))) return VasuLocalIntent.GoHome
        if (containsAny(normalized, listOf("recents", "recent", "recent apps", "recent app kholo", "recents kholo"))) return VasuLocalIntent.OpenRecents
        if (containsAny(normalized, listOf("scroll down", "neeche scroll", "niche scroll", "scroll neeche", "scroll niche", "neeche karo"))) return VasuLocalIntent.ScrollDown
        if (containsAny(normalized, listOf("scroll up", "upar scroll", "scroll upar", "upar karo"))) return VasuLocalIntent.ScrollUp
        if (containsAny(normalized, listOf("swipe left", "left swipe", "baaye swipe", "baye swipe"))) return VasuLocalIntent.SwipeLeft
        if (containsAny(normalized, listOf("swipe right", "right swipe", "daaye swipe", "daye swipe"))) return VasuLocalIntent.SwipeRight

        val openApp = extractOpenApp(normalized)
        if (!openApp.isNullOrBlank()) return VasuLocalIntent.OpenApp(openApp)

        return VasuLocalIntent.Unknown
    }

    private fun containsAny(input: String, patterns: List<String>): Boolean = patterns.any(input::contains)

    private fun extractOpenApp(input: String): String? {
        val prefixes = listOf(
            "open app ",
            "open ",
            "launch ",
            "khol do ",
            "khol ",
            "kholo ",
            "open karo ",
            "launch karo "
        )
        return prefixes.firstNotNullOfOrNull { prefix ->
            if (!input.startsWith(prefix)) return@firstNotNullOfOrNull null
            input.removePrefix(prefix)
                .removeSuffix(" karo")
                .removeSuffix(" kar do")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }
}
