package com.vasu.ai.core

import java.util.Locale

class VasuLocalIntentEngine {
    fun parse(input: String): VasuLocalIntent {
        val normalized = normalize(input)
        if (normalized.isBlank()) return VasuLocalIntent.Unknown

        if (containsAny(normalized, listOf("stop", "ruk jao", "ruk ja", "cancel", "band karo", "bas"))) return VasuLocalIntent.Stop
        if (containsAny(normalized, listOf("back", "go back", "peeche jao", "piche jao", "wapas jao", "वापस जाओ"))) return VasuLocalIntent.GoBack
        if (containsAny(normalized, listOf("home", "go home", "ghar jao", "home jao"))) return VasuLocalIntent.GoHome
        if (containsAny(normalized, listOf("recents", "recent apps", "recent app kholo"))) return VasuLocalIntent.OpenRecents
        if (containsAny(normalized, listOf("scroll down", "neeche scroll", "niche scroll", "scroll neeche", "scroll niche"))) return VasuLocalIntent.ScrollDown
        if (containsAny(normalized, listOf("scroll up", "upar scroll", "scroll upar"))) return VasuLocalIntent.ScrollUp
        if (containsAny(normalized, listOf("swipe left", "left swipe", "baaye swipe", "baye swipe"))) return VasuLocalIntent.SwipeLeft
        if (containsAny(normalized, listOf("swipe right", "right swipe", "daaye swipe", "daye swipe"))) return VasuLocalIntent.SwipeRight

        val openApp = extractOpenApp(normalized)
        if (!openApp.isNullOrBlank()) return VasuLocalIntent.OpenApp(openApp)

        return VasuLocalIntent.Unknown
    }

    private fun normalize(input: String): String = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun containsAny(input: String, patterns: List<String>): Boolean = patterns.any(input::contains)

    private fun extractOpenApp(input: String): String? {
        val prefixes = listOf("open ", "open app ", "kholo ", "khol do ", "launch ")
        return prefixes.firstNotNullOfOrNull { prefix ->
            if (!input.startsWith(prefix)) return@firstNotNullOfOrNull null
            input.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
        }
    }
}
