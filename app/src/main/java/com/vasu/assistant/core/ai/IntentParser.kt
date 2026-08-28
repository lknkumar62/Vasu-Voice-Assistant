package com.vasu.assistant.core.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed intent from user command
 */
data class ParsedIntent(
    val intent: IntentType,
    val entities: Map<String, String> = emptyMap(),
    val confidence: Float = 0f,
    val rawText: String = ""
)

/**
 * Supported intent types
 */
enum class IntentType {
    // App control
    OPEN_APP,
    CLOSE_APP,

    // UI interaction
    CLICK,
    TYPE_TEXT,
    SCROLL,
    READ_SCREEN,

    // Communication
    SEND_MESSAGE,
    MAKE_CALL,
    READ_NOTIFICATION,

    // Device
    SET_VOLUME,
    TOGGLE_TORCH,
    CREATE_ALARM,

    // Information
    SEARCH_WEB,
    GET_WEATHER,
    GET_TIME,

    // Navigation
    GO_BACK,
    GO_HOME,

    // System
    RUN_MISSION,
    ENROLL_VOICE,

    // Conversation
    CHAT,
    UNKNOWN
}

/**
 * IntentParser - Parses user text into structured intents.
 *
 * Uses keyword matching and pattern recognition to understand
 * user commands and extract entities.
 */
@Singleton
class IntentParser @Inject constructor() {

    private val intentPatterns = mapOf(
        IntentType.OPEN_APP to listOf("open", "launch", "start", "kholo", "chalaao"),
        IntentType.CLOSE_APP to listOf("close", "stop", "band", "karo"),
        IntentType.CLICK to listOf("click", "tap", "press", "dabao", "maaro"),
        IntentType.TYPE_TEXT to listOf("type", "enter", "likho", "daalo", "search"),
        IntentType.SCROLL to listOf("scroll", "swipe", "upar", "niche"),
        IntentType.READ_SCREEN to listOf("read", "screen", "padho", "kya hai"),
        IntentType.SEND_MESSAGE to listOf("message", "bhejo", "send", "sms", "whatsapp"),
        IntentType.MAKE_CALL to listOf("call", "phone", "call karo", "phone lagao"),
        IntentType.READ_NOTIFICATION to listOf("notification", "notification padho", "alert"),
        IntentType.SET_VOLUME to listOf("volume", "awaaz", "sound"),
        IntentType.TOGGLE_TORCH to listOf("torch", "flashlight", "light", "on kar", "off kar"),
        IntentType.CREATE_ALARM to listOf("alarm", "reminder", "yaad dila"),
        IntentType.SEARCH_WEB to listOf("search", "google", "dhundho", "khojho"),
        IntentType.GET_WEATHER to listOf("weather", "mausam", "temperature"),
        IntentType.GET_TIME to listOf("time", "kitne baje", "waqt"),
        IntentType.GO_BACK to listOf("back", "piche", "peeche"),
        IntentType.GO_HOME to listOf("home", "screen", "home page"),
        IntentType.RUN_MISSION to listOf("mission", "task", "kaam"),
        IntentType.ENROLL_VOICE to listOf("enroll", "voice add", "voice save"),
        IntentType.CHAT to listOf("tell me", "batao", "explain", "samjhao", "kya ho raha")
    )

    /**
     * Parse user text into intent
     */
    fun parse(text: String): ParsedIntent {
        val lowerText = text.lowercase().trim()

        // Try each intent pattern
        for ((intentType, patterns) in intentPatterns) {
            for (pattern in patterns) {
                if (lowerText.contains(pattern)) {
                    val entities = extractEntities(lowerText, intentType)
                    return ParsedIntent(
                        intent = intentType,
                        entities = entities,
                        confidence = calculateConfidence(lowerText, pattern),
                        rawText = text
                    )
                }
            }
        }

        // Default to chat
        return ParsedIntent(
            intent = IntentType.CHAT,
            entities = mapOf("query" to text),
            confidence = 0.5f,
            rawText = text
        )
    }

    /**
     * Extract entities from text based on intent
     */
    private fun extractEntities(text: String, intent: IntentType): Map<String, String> {
        val entities = mutableMapOf<String, String>()

        when (intent) {
            IntentType.OPEN_APP -> {
                val appName = extractAfterPatterns(text, listOf("open", "launch", "start", "kholo", "chalaao"))
                if (appName.isNotBlank()) {
                    entities["package"] = mapPackageName(appName)
                    entities["app_name"] = appName
                }
            }
            IntentType.CLICK -> {
                val textToClick = extractAfterPatterns(text, listOf("click", "tap", "press", "dabao"))
                if (textToClick.isNotBlank()) entities["text"] = textToClick
            }
            IntentType.TYPE_TEXT -> {
                val textToType = extractAfterPatterns(text, listOf("type", "enter", "likho", "daalo"))
                if (textToType.isNotBlank()) entities["text"] = textToType
            }
            IntentType.SEND_MESSAGE -> {
                entities["message"] = text
            }
            IntentType.MAKE_CALL -> {
                entities["contact"] = text
            }
            IntentType.SEARCH_WEB -> {
                val query = extractAfterPatterns(text, listOf("search", "google", "dhundho"))
                if (query.isNotBlank()) entities["query"] = query
            }
            IntentType.SET_VOLUME -> {
                val level = extractNumber(text)
                if (level != null) entities["level"] = level.toString()
            }
            IntentType.CREATE_ALARM -> {
                val time = extractTime(text)
                if (time != null) entities["time"] = time
            }
            else -> {}
        }

        return entities
    }

    private fun extractAfterPatterns(text: String, patterns: List<String>): String {
        for (pattern in patterns) {
            val index = text.indexOf(pattern)
            if (index >= 0) {
                return text.substring(index + pattern.length).trim()
            }
        }
        return ""
    }

    private fun extractNumber(text: String): Int? {
        val regex = Regex("\\d+")
        return regex.find(text)?.value?.toIntOrNull()
    }

    private fun extractTime(text: String): String? {
        val regex = Regex("(\\d{1,2})[:.](\\d{2})")
        return regex.find(text)?.value
    }

    private fun calculateConfidence(text: String, pattern: String): Float {
        return if (text.contains(pattern)) 0.8f else 0.5f
    }

    private fun mapPackageName(appName: String): String {
        return when {
            appName.contains("whatsapp") -> "com.whatsapp"
            appName.contains("youtube") -> "com.google.android.youtube"
            appName.contains("chrome") -> "com.android.chrome"
            appName.contains("camera") -> "com.android.camera"
            appName.contains("settings") -> "com.android.settings"
            appName.contains("phone") -> "com.android.dialer"
            appName.contains("messages") -> "com.android.mms"
            appName.contains("gallery") || appName.contains("photos") -> "com.google.android.apps.photos"
            appName.contains("maps") -> "com.google.android.apps.maps"
            appName.contains("play store") -> "com.android.vending"
            appName.contains("gmail") -> "com.google.android.gm"
            appName.contains("clock") -> "com.google.android.deskclock"
            appName.contains("calculator") -> "com.android.calculator2"
            appName.contains("files") -> "com.android.documentsui"
            appName.contains("music") || appName.contains("spotify") -> "com.spotify.music"
            else -> "com.android.settings" // Fallback
        }
    }
}
