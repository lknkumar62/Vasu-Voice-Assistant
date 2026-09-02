package com.vasu.assistant.core.ai

import com.vasu.assistant.core.automation.ActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AssistantLanguage {
    HINDI,
    ENGLISH
}

/**
 * Single Canonical Hindi Response Normalizer & Language Router.
 *
 * Requirements:
 * 1. Default assistant response language is Hindi (Devanagari).
 * 2. Normal conversation does not use Roman Hindi / Hinglish.
 * 3. Exact canonical text produced here is shared identically between UI and Local TTS.
 * 4. Lightweight local language detection and routing (no paid APIs).
 * 5. Handles device command results, conversational turns, error messages, and Roman Hindi transliteration.
 */
@Singleton
class HindiResponseNormalizer @Inject constructor() {

    private val _preferredLanguage = MutableStateFlow(AssistantLanguage.HINDI)
    val preferredLanguage: StateFlow<AssistantLanguage> = _preferredLanguage.asStateFlow()

    fun setLanguage(language: AssistantLanguage) {
        _preferredLanguage.value = language
    }

    /**
     * Inspects user input for explicit language switching commands.
     * Returns a confirmation message if a switch occurred, or null otherwise.
     */
    fun checkLanguageSwitchCommand(input: String): String? {
        val lower = input.lowercase(Locale.ROOT).trim()

        // English switch requests
        if (lower.contains("reply in english") ||
            lower.contains("speak in english") ||
            lower.contains("talk in english") ||
            lower.contains("answer in english") ||
            lower.contains("switch to english") ||
            lower == "english" ||
            lower == "english please"
        ) {
            _preferredLanguage.value = AssistantLanguage.ENGLISH
            return "Sure! I will reply in English from now on."
        }

        // Hindi switch requests
        if (lower.contains("reply in hindi") ||
            lower.contains("speak in hindi") ||
            lower.contains("talk in hindi") ||
            lower.contains("hindi mein bolo") ||
            lower.contains("hindi me bolo") ||
            lower.contains("हिंदी में बोलो") ||
            lower.contains("हिंदी में बात करो") ||
            lower.contains("हिंदी में जवाब दो") ||
            lower == "hindi" ||
            lower == "hindi please"
        ) {
            _preferredLanguage.value = AssistantLanguage.HINDI
            return "नमस्ते! अब से मैं आपसे स्वाभाविक हिंदी में बात करूँगी।"
        }

        return null
    }

    /**
     * Returns natural Hindi responses for common greetings and fast conversational inputs.
     */
    fun getConversationalResponse(input: String): String? {
        val lower = input.lowercase(Locale.ROOT).trim()

        // Maya alias addressing
        if (lower.contains("maya") || lower == "hello maya" || lower == "hey maya" || lower == "hi maya") {
            return "अरे, मैं वासु हूँ। 😊 माया नहीं। बताओ, मैं तुम्हारी किस तरह मदद करूँ?"
        }

        // Greetings
        if (lower == "hello vasu" || lower == "hi vasu" || lower == "hey vasu" || lower == "namaste vasu") {
            return "नमस्ते! 😊 बताओ, मैं तुम्हारी किस तरह मदद करूँ?"
        }

        if (lower == "hello" || lower == "hi" || lower == "hey" || lower == "namaste" || lower == "नमस्ते" || lower == "हेलो") {
            return "नमस्ते! 😊 बहुत दिनों बाद बात हुई। कैसे हो?"
        }

        // Status queries / small talk
        if (lower == "ha thik hai tum batao" || lower == "haan theek hai tum batao" || lower == "ha theek hai tum batao" ||
            lower == "main theek hoon" || lower == "theek hoon" || lower == "all good" || lower == "sab theek hai"
        ) {
            return "मैं भी बिल्कुल ठीक हूँ। तुमसे बात करके और अच्छा लग रहा है। बताओ, आज क्या चल रहा है?"
        }

        if (lower == "kaise ho" || lower == "kya haal hai" || lower == "kya haal chal" || lower == "kya chal raha hai" ||
            lower == "how are you" || lower.contains("क्या हाल") || lower.contains("कैसे हो")
        ) {
            return "नमस्ते बॉस! क्या हाल-चाल हैं? बहुत दिनों बाद मुलाकात हुई। सब ठीक है ना?"
        }

        if (lower == "what is your name" || lower == "what is your name?" || lower == "who are you" ||
            lower == "tumhara naam kya hai" || lower == "tum kaun ho" || lower.contains("तुम्हारा नाम") || lower.contains("तुम कौन हो")
        ) {
            return if (_preferredLanguage.value == AssistantLanguage.ENGLISH) {
                "My name is VASU. I am your voice assistant."
            } else {
                "मेरा नाम वासु है। मैं आपकी वॉइस असिस्टेंट हूँ।"
            }
        }

        return null
    }

    /**
     * Converts an ActionResult into natural, conversational Hindi Devanagari text.
     */
    fun describeActionResult(result: ActionResult, rawCommand: String = ""): String {
        val action = result.action.lowercase(Locale.ROOT)
        val raw = rawCommand.lowercase(Locale.ROOT)

        if (!result.success) {
            return "माफ़ कीजिए, यह काम नहीं हो पाया: ${translateMessageToHindi(result.message)}"
        }

        return when {
            action.contains("torch") || raw.contains("torch") || raw.contains("टॉर्च") || raw.contains("flashlight") -> {
                if (raw.contains("off") || raw.contains("band") || raw.contains("बंद") || result.message.contains("off", ignoreCase = true)) {
                    "टॉर्च बंद कर दी है।"
                } else {
                    "ठीक है, टॉर्च चालू कर दी है।"
                }
            }
            action == "open_app" || raw.contains("open") || raw.contains("kholo") || raw.contains("खोलो") -> {
                val appName = result.message.removePrefix("Opened ").trim()
                if (appName.isNotBlank()) "$appName खोल दिया गया है।" else "ऐप खोल दिया गया है।"
            }
            action == "set_volume" || action == "volume" -> {
                val level = Regex("\\d+").find(result.message)?.value ?: ""
                if (level.isNotBlank()) "वॉल्यूम $level% पर सेट कर दिया गया है।" else "वॉल्यूम सेट कर दिया गया है।"
            }
            action == "volume_up" -> "वॉल्यूम बढ़ा दिया गया है।"
            action == "volume_down" -> "वॉल्यूम कम कर दिया गया है।"
            action.contains("bluetooth") -> {
                if (raw.contains("off") || raw.contains("band") || raw.contains("बंद") || result.message.contains("off", ignoreCase = true)) {
                    "ब्लूटूथ बंद कर दिया गया है।"
                } else {
                    "ब्लूटूथ चालू कर दिया गया है।"
                }
            }
            action.contains("media") -> {
                when {
                    action.contains("next") -> "अगला गाना चला दिया गया है।"
                    action.contains("previous") -> "पिछला गाना चला दिया गया है।"
                    else -> "मीडिया प्ले/पॉज़ कर दिया गया है।"
                }
            }
            action == "create_alarm" -> {
                val time = Regex("\\d{1,2}:\\d{2}").find(result.message)?.value ?: "दिए गए समय"
                "$time के लिए अलार्म सेट कर दिया गया है।"
            }
            action == "search_web" -> {
                val query = result.message.removePrefix("Searching for ").trim()
                if (query.isNotBlank()) "$query के लिए खोजा जा रहा है..." else "सर्च किया जा रहा है..."
            }
            action == "back" || action == "press_back" -> "वापस चले गए हैं।"
            action == "home" || action == "press_home" -> "होम स्क्रीन पर चले गए हैं।"
            action == "click" -> "क्लिक कर दिया गया है।"
            action == "type" || action == "type_text" -> "टेक्स्ट टाइप कर दिया गया है।"
            action == "make_call" -> "कॉल लगाया जा रहा है।"
            action == "send_message" || action == "send_sms" -> "मैसेज भेजा जा रहा है।"
            action == "whatsapp" -> "व्हाट्सएप खोला जा रहा है।"
            action == "battery" -> {
                val level = Regex("\\d+").find(result.message)?.value ?: ""
                if (level.isNotBlank()) "बैटरी अभी $level% है।" else "बैटरी की जानकारी प्राप्त हो गई है।"
            }
            else -> translateMessageToHindi(result.message)
        }
    }

    /**
     * Translates common English/Roman Hindi system output into natural conversational Hindi Devanagari.
     */
    fun translateMessageToHindi(message: String): String {
        var text = message.trim()

        // Replace known Roman Hindi phrases with Devanagari
        ROMAN_HINDI_MAP.forEach { (roman, devanagari) ->
            text = text.replace(Regex("(?i)\\b$roman\\b"), devanagari)
        }

        // Common English system phrases
        text = text.replace(Regex("(?i)\\bFlashlight turned on\\b"), "टॉर्च चालू कर दी है।")
        text = text.replace(Regex("(?i)\\bFlashlight turned off\\b"), "टॉर्च बंद कर दी है।")
        text = text.replace(Regex("(?i)\\bBluetooth turned on\\b"), "ब्लूटूथ चालू कर दिया गया है।")
        text = text.replace(Regex("(?i)\\bBluetooth turned off\\b"), "ब्लूटूथ बंद कर दिया गया है।")
        text = text.replace(Regex("(?i)\\bVolume set to (\\d+)%\\b"), "वॉल्यूम $1% पर सेट कर दिया गया है।")
        text = text.replace(Regex("(?i)\\bAlarm created for (.+)\\b"), "$1 के लिए अलार्म सेट कर दिया गया है।")
        text = text.replace(Regex("(?i)\\bOpened (.+)\\b"), "$1 खोल दिया गया है।")
        text = text.replace(Regex("(?i)\\bOnline AI unavailable.*"), "ऑनलाइन एआई उपलब्ध नहीं है — अभी केवल ऑफ़लाइन कमांड काम करेंगे।")

        return text
    }

    /**
     * Ensures the final generated response is canonical, clean, and in natural Hindi Devanagari.
     */
    fun canonicalize(response: String): String {
        if (response.isBlank()) return ""

        if (_preferredLanguage.value == AssistantLanguage.ENGLISH) {
            return response.trim()
        }

        return translateMessageToHindi(response).trim()
    }

    companion object {
        private val ROMAN_HINDI_MAP = mapOf(
            "kya haal chal hain" to "क्या हाल-चाल हैं",
            "kya haal hai" to "क्या हाल है",
            "kya haal hain" to "क्या हाल हैं",
            "main theek hoon" to "मैं ठीक हूँ",
            "main bhi ekdum mast" to "मैं भी बिल्कुल मस्त हूँ",
            "kuch galat ho gaya" to "कुछ गलत हो गया",
            "ek baar phir bolo" to "एक बार फिर बोलो",
            "suno" to "सुनो",
            "haan bolo" to "हाँ बोलो",
            "theek hai" to "ठीक है",
            "kya karna hai" to "क्या करना है",
            "batao" to "बताओ",
            "namaste" to "नमस्ते",
            "shukriya" to "शुक्रिया",
            "dhanyawad" to "धन्यवाद",
            "chalo" to "चलो"
        )
    }
}
