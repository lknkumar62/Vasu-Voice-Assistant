package com.vasu.assistant.core.ai

/**
 * Centralized language/script detection layer for VASU.
 *
 * PURPOSE:
 * The user's input script/style must be mirrored in the assistant response.
 * Roman Hindi input must NEVER be answered in Devanagari unless the user
 * used Devanagari. English input must be answered in English.
 *
 * This layer is purely local (no paid APIs, no network) and is consulted
 * before every AI request. The detected style is passed into the AI request
 * as an explicit response-language instruction.
 *
 * Detected styles:
 * - ROMAN_HINDI      : Hindi written in Latin script ("hello vasu kya hal hai")
 * - DEVANAGARI_HINDI : Hindi written in Devanagari ("हेलो वासु क्या हाल है")
 * - ENGLISH          : English ("Hello Vasu, how are you?")
 * - HINGLISH         : Roman mix with English/technical content
 *                      ("Vasu mujhe Kotlin coroutines samjhao")
 * - OTHER            : anything else (other scripts, empty, unclassifiable)
 */
enum class DetectedStyle {
    ROMAN_HINDI,
    DEVANAGARI_HINDI,
    ENGLISH,
    HINGLISH,
    OTHER
}

enum class ResponseScript {
    ROMAN,
    DEVANAGARI,
    ENGLISH,
    OTHER
}

data class DetectedLanguage(
    val style: DetectedStyle,
    val script: ResponseScript,
    /** Explicit instruction injected into the AI system prompt. */
    val instruction: String
)

object LanguageDetector {

    private val DEVANAGARI_RANGE = Regex("[\\u0900-\\u097F]")

    /**
     * Technical identifiers that must never be translated or transliterated.
     * Kept here so both detection and prompt construction share one list.
     */
    val TECHNICAL_TERMS = listOf(
        "Kotlin", "Android", "API", "Gemini", "OpenRouter",
        "GitHub", "ADB", "Gradle"
    )

    /**
     * Whole-word Roman Hindi markers. A Latin-script sentence is only treated
     * as Hindi/Hinglish when it contains at least one of these — plain
     * English such as "Hello Vasu, explain Kotlin coroutines." must stay
     * English and must NOT be forced into Hindi.
     *
     * Deliberately EXCLUDED: "the" (English article; थे is covered by
     * "theek"/context), "main" (English noun; मैं is covered by
     * "theek"/"hoon"/"batao" companions), "ya" (English slang; "yaar" is
     * listed separately). Single-letter collisions ("me" vs "mein") are
     * avoided by listing only the full forms.
     */
    private val ROMAN_HINDI_MARKERS = setOf(
        "kya", "kaisa", "kaisi", "kaise", "hai", "hain", "ho", "hal", "haal",
        "chal", "raha", "rahi", "rahe", "mujhe", "mujhko", "tum", "tumhe",
        "tumhara", "tumhari", "aap", "aapko", "aapka", "aapki", "mera", "meri",
        "mere", "tera", "teri", "tere", "karo", "karna", "karne", "karke",
        "kiya", "kiye", "batao", "bataiye", "bataye", "samjhao", "samjha",
        "samajh", "theek", "thik", "acha", "achha", "accha", "bahut", "bahot",
        "bohot", "liye", "mein", "mai", "hoon", "hun", "nahin",
        "nahi", "mat", "zara", "koi", "kuch", "kyon", "kyun", "kab", "kahan",
        "kidhar", "kaun", "kitna", "kitne", "kitni", "chahiye", "chahta",
        "chahti", "chahte", "karo", "kar", "bata", "suno", "dekho", "dikhao",
        "kholo", "band", "chalu", "chaalu", "jalao", "bujhao", "badhao",
        "badh", "zyada", "abhi", "aaj", "shukriya", "dhanyavad", "dhanyawad",
        "namaste", "namaskar", "kaam", "madad", "tarah", "kaise", "bhai",
        "yaar", "arre", "chal", "chalo", "theek", "mast", "ekdum", "gazab",
        "sunao", "kaho", "bolo", "bol", "tumhara", "hamara", "hamari",
        "apna", "apni", "kaun", "kis", "kisko", "jara", "thoda", "jyada",
        "pehle", "baad", "phir", "wapas", "idhar", "udhar", "yahaan",
        "vahaan", "kaafi", "bilkul", "bas", "sirf", "lekin", "magar",
        "aur", "nah", "gaya", "gayi", "gaye", "hua", "hui", "hue",
        "hoga", "hogi", "hoge", "tha", "thi", "wala", "wali", "wale",
        "karke", "karta", "karti", "karte", "hota", "hoti", "hote",
        "padhai", "seekh", "seekhna", "bana", "banana", "chalana", "lagana",
        "nikal", "dale", "rakh", "rakho", "pakad", "chhod", "tod", "jod",
        "ghuma", "ghum", "chalao", "rok", "roko", "uthao", "bithao", "likho",
        "padho", "khelo", "khao", "piyo", "so", "sojao", "utho", "jaago"
    )

    private const val PERSONALITY_RULES =
        "Keep casual replies short and natural; give detailed answers only for " +
            "complex technical questions. Do not introduce yourself as VASU " +
            "(\"Main Vasu hoon...\") unless the context actually requires it. " +
            "Do not append generic follow-up questions after every reply; " +
            "answer the actual user request."

    private const val TECH_RULE =
        "Preserve technical terms and identifiers exactly as written " +
            "(Kotlin, Android, API, Gemini, OpenRouter, GitHub, ADB, Gradle); " +
            "do not translate or transliterate them."

    fun detect(input: String): DetectedLanguage {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return DetectedLanguage(
                style = DetectedStyle.OTHER,
                script = ResponseScript.OTHER,
                instruction = "Reply in the user's language and script. $PERSONALITY_RULES $TECH_RULE"
            )
        }

        // RULE 2 — Devanagari script always wins: user wrote Devanagari.
        if (DEVANAGARI_RANGE.containsMatchIn(trimmed)) {
            return DetectedLanguage(
                style = DetectedStyle.DEVANAGARI_HINDI,
                script = ResponseScript.DEVANAGARI,
                instruction = "Reply in natural Hindi using Devanagari script. " +
                    PERSONALITY_RULES + " " + TECH_RULE
            )
        }

        // Latin script: look for genuine Hindi content, not just Latin chars.
        val words = trimmed.lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.isNotBlank() }
        val hindiHits = words.count { it in ROMAN_HINDI_MARKERS }

        if (hindiHits == 0) {
            // RULE 3 — plain English (e.g. "Hello Vasu, explain Kotlin coroutines.")
            return DetectedLanguage(
                style = DetectedStyle.ENGLISH,
                script = ResponseScript.ENGLISH,
                instruction = "Reply in English. " +
                    PERSONALITY_RULES + " " + TECH_RULE
            )
        }

        // Hindi markers present in Latin script. Distinguish mostly-Hindi
        // (ROMAN_HINDI) from Hindi/English mix (HINGLISH); both answer in Roman.
        val hindiRatio = hindiHits.toFloat() / words.size.coerceAtLeast(1)
        val style = if (hindiRatio >= 0.35 || hindiHits >= 3) {
            DetectedStyle.ROMAN_HINDI
        } else {
            DetectedStyle.HINGLISH
        }
        return DetectedLanguage(
            style = style,
            script = ResponseScript.ROMAN,
            instruction = "Reply in natural Roman Hindi/Hinglish using Latin " +
                "characters only. Do not use Devanagari script unless the user " +
                "uses Devanagari. Never transliterate the user's Roman Hindi " +
                "into Devanagari. " + PERSONALITY_RULES + " " + TECH_RULE
        )
    }

    /** True when the input was written in Devanagari script. */
    fun isDevanagari(input: String): Boolean = DEVANAGARI_RANGE.containsMatchIn(input)
}
