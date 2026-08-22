package com.vasu.ai.core

import java.util.Locale

class VasuCommandNormalizer {
    fun normalize(input: String): String = input
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\u0000-\\u001F]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun normalizeHindiHinglish(input: String): String {
        var text = normalize(input)
        val replacements = mapOf(
            "पीछे जाओ" to "back jao",
            "पीछे जाना" to "back jao",
            "वापस जाओ" to "back jao",
            "होम जाओ" to "home jao",
            "घर जाओ" to "home jao",
            "ऊपर स्क्रॉल" to "scroll up",
            "नीचे स्क्रॉल" to "scroll down",
            "ऊपर स्क्रोल" to "scroll up",
            "नीचे स्क्रोल" to "scroll down",
            "रिसेंट ऐप्स" to "recent apps",
            "बंद करो" to "stop",
            "रुक जाओ" to "stop",
            "रुक जा" to "stop"
        )
        replacements.forEach { (from, to) -> text = text.replace(from, to) }
        return normalize(text)
    }
}
