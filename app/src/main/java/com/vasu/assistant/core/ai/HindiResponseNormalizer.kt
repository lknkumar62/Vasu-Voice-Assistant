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
 * Single Canonical Response Normalizer & Language Router.
 *
 * SCRIPT-MIRRORING CONTRACT (must never be broken):
 * 1. The user's input script/style has priority and is never changed
 *    automatically. Roman Hindi input -> Roman Hindi/Hinglish response.
 *    Devanagari input -> Devanagari response. English input -> English.
 * 2. Normal conversation mirrors the user's script; Devanagari is used
 *    ONLY when the user wrote Devanagari (or explicitly asked for Hindi
 *    script via [checkLanguageSwitchCommand]).
 * 3. The exact canonical text produced here is shared identically between
 *    the Chat UI and TTS. TTS-only cleanup lives in toSpeakableText.
 * 4. Lightweight local language detection via [LanguageDetector] (no paid APIs).
 * 5. Handles device command results, conversational turns, error messages.
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
     * Returns natural conversational responses for common greetings and fast
     * conversational inputs, mirroring the user's script.
     *
     * Roman Hindi input gets a Roman Hinglish reply, Devanagari input gets a
     * Devanagari reply, English input gets an English reply. The script is
     * never flipped automatically.
     */
    fun getConversationalResponse(input: String): String? {
        val lower = input.lowercase(Locale.ROOT).trim()
        val detected = LanguageDetector.detect(input)
        val roman = detected.script == ResponseScript.ROMAN
        val english = detected.style == DetectedStyle.ENGLISH

        // Maya alias addressing — Vasu branding (Maya parity: handle legacy name)
        if (lower.contains("maya") || lower == "hello maya" || lower == "hey maya" || lower == "hi maya") {
            return if (english) {
                "Hey, I am Vasu. 😊 Tell me, how can I help you?"
            } else if (roman) {
                "Are, main Vasu hoon. 😊 Batao, main tumhari kis tarah madad karoon?"
            } else {
                "अरे, मैं वासु हूँ। 😊 बताओ, मैं तुम्हारी किस तरह मदद करूँ?"
            }
        }

        // Greetings
        if (lower == "hello vasu" || lower == "hi vasu" || lower == "hey vasu" || lower == "namaste vasu") {
            return if (english) {
                "Hello! 😊 Tell me, how can I help you?"
            } else if (roman) {
                "Namaste! 😊 Batao, main tumhari kis tarah madad karoon?"
            } else {
                "नमस्ते! 😊 बताओ, मैं तुम्हारी किस तरह मदद करूँ?"
            }
        }

        if (lower == "hello" || lower == "hi" || lower == "hey" || lower == "namaste" || lower == "नमस्ते" || lower == "हेलो") {
            return if (english) {
                "Hello! 😊 Good to talk to you. How are you?"
            } else if (roman) {
                "Namaste! 😊 Bahut dino baad baat hui. Kaise ho?"
            } else {
                "नमस्ते! 😊 बहुत दिनों बाद बात हुई। कैसे हो?"
            }
        }

        // Status queries / small talk (exact + contains, both scripts)
        if (lower == "ha thik hai tum batao" || lower == "haan theek hai tum batao" || lower == "ha theek hai tum batao" ||
            lower == "main theek hoon" || lower == "theek hoon" || lower == "all good" || lower == "sab theek hai"
        ) {
            return if (english) {
                "I am doing great too. Good talking to you! So, what is going on today?"
            } else if (roman) {
                "Main bhi bilkul theek hoon. Tumse baat karke aur achha lag raha hai. Batao, aaj kya chal raha hai?"
            } else {
                "मैं भी बिल्कुल ठीक हूँ। तुमसे बात करके और अच्छा लग रहा है। बताओ, आज क्या चल रहा है?"
            }
        }

        if (lower == "kaise ho" || lower == "kya haal hai" || lower == "kya haal chal" || lower == "kya chal raha hai" ||
            lower == "how are you" || lower.contains("क्या हाल") || lower.contains("कैसे हो") ||
            lower.contains("kya haal") || lower.contains("kya hal") || lower.contains("kaise ho") ||
            lower.contains("kya chal raha")
        ) {
            return if (english) {
                "Hey! I am doing well. Long time no see — is everything alright?"
            } else if (roman || lower.contains("kya haal") || lower.contains("kya hal") ||
                lower.contains("kaise ho") || lower.contains("kya chal raha")
            ) {
                "Main bilkul theek hoon! 😊 Tum batao, aaj kya chal raha hai?"
            } else {
                "नमस्ते बॉस! क्या हाल-चाल हैं? बहुत दिनों बाद मुलाकात हुई। सब ठीक है ना?"
            }
        }

        if (lower == "what is your name" || lower == "what is your name?" || lower == "who are you" ||
            lower == "tumhara naam kya hai" || lower == "tum kaun ho" || lower.contains("तुम्हारा नाम") || lower.contains("तुम कौन हो")
        ) {
            return if (_preferredLanguage.value == AssistantLanguage.ENGLISH || english) {
                "My name is VASU. I am your voice assistant."
            } else if (roman) {
                "Mera naam Vasu hai. Main aapki voice assistant hoon."
            } else {
                "मेरा नाम वासु है। मैं आपकी वॉइस असिस्टेंट हूँ।"
            }
        }

        return null
    }

    /**
     * Converts an ActionResult into natural conversational text, mirroring
     * the script of the original user command ([rawCommand]).
     *
     * Roman Hinglish commands get Roman confirmations, Devanagari commands
     * get Devanagari confirmations, English commands get English ones.
     */
    fun describeActionResult(result: ActionResult, rawCommand: String = ""): String {
        val action = result.action.lowercase(Locale.ROOT)
        val raw = rawCommand.lowercase(Locale.ROOT)
        val detected = LanguageDetector.detect(rawCommand)
        val roman = detected.script == ResponseScript.ROMAN
        val english = detected.style == DetectedStyle.ENGLISH

        if (!result.success) {
            val detail = result.message
            return if (english) {
                "Sorry, that did not work: $detail"
            } else if (roman) {
                "Maaf kijiye, ye kaam nahi ho paya: $detail"
            } else {
                "माफ़ कीजिए, यह काम नहीं हो पाया: ${translateMessageToHindi(result.message)}"
            }
        }

        // Script-aware confirmations: pick() keeps the user's script.
        // romanText = Roman Hinglish, devaText = Devanagari, engText = English.
        fun pick(romanText: String, devaText: String, engText: String): String =
            if (english) engText else if (roman) romanText else devaText

        return when {
            action.contains("torch") || raw.contains("torch") || raw.contains("टॉर्च") || raw.contains("flashlight") -> {
                if (raw.contains("off") || raw.contains("band") || raw.contains("बंद") || result.message.contains("off", ignoreCase = true)) {
                    pick("Torch band kar di hai.", "टॉर्च बंद कर दी है।", "Torch is now off.")
                } else {
                    pick("Theek hai, torch chalu kar di hai.", "ठीक है, टॉर्च चालू कर दी है।", "Done, torch is now on.")
                }
            }
            action == "open_app" || raw.contains("open") || raw.contains("kholo") || raw.contains("खोलो") -> {
                val appName = result.message.removePrefix("Opened ").trim()
                if (appName.isNotBlank()) pick(
                    "$appName khol diya gaya hai.",
                    "$appName खोल दिया गया है।",
                    "Opened $appName."
                ) else pick("App khol diya gaya hai.", "ऐप खोल दिया गया है।", "App opened.")
            }
            action == "set_volume" || action == "volume" -> {
                val level = Regex("\\d+").find(result.message)?.value ?: ""
                if (level.isNotBlank()) pick(
                    "Volume $level% par set kar diya gaya hai.",
                    "वॉल्यूम $level% पर सेट कर दिया गया है।",
                    "Volume set to $level%."
                ) else pick("Volume set kar diya gaya hai.", "वॉल्यूम सेट कर दिया गया है।", "Volume updated.")
            }
            action == "volume_up" -> pick("Volume badha diya gaya hai.", "वॉल्यूम बढ़ा दिया गया है।", "Volume increased.")
            action == "volume_down" -> pick("Volume kam kar diya gaya hai.", "वॉल्यूम कम कर दिया गया है।", "Volume decreased.")
            action.contains("bluetooth") -> {
                if (raw.contains("off") || raw.contains("band") || raw.contains("बंद") || result.message.contains("off", ignoreCase = true)) {
                    pick("Bluetooth band kar diya gaya hai.", "ब्लूटूथ बंद कर दिया गया है।", "Bluetooth is now off.")
                } else {
                    pick("Bluetooth chalu kar diya gaya hai.", "ब्लूटूथ चालू कर दिया गया है।", "Bluetooth is now on.")
                }
            }
            action.contains("media") -> {
                when {
                    action.contains("next") -> pick("Agla gaana chala diya gaya hai.", "अगला गाना चला दिया गया है।", "Playing the next track.")
                    action.contains("previous") -> pick("Pichla gaana chala diya gaya hai.", "पिछला गाना चला दिया गया है।", "Playing the previous track.")
                    else -> pick("Media play/pause kar diya gaya hai.", "मीडिया प्ले/पॉज़ कर दिया गया है।", "Media play/paused.")
                }
            }
            action == "create_alarm" -> {
                val time = Regex("\\d{1,2}:\\d{2}").find(result.message)?.value ?: "diye gaye samay"
                if (roman || english) pick(
                    "$time ke liye alarm set kar diya gaya hai.",
                    "$time के लिए अलार्म सेट कर दिया गया है।",
                    "Alarm set for $time."
                ) else "${Regex("\\d{1,2}:\\d{2}").find(result.message)?.value ?: "दिए गए समय"} के लिए अलार्म सेट कर दिया गया है।"
            }
            action == "set_timer" -> {
                val sec = Regex("\\d+").find(result.message)?.value ?: ""
                if (sec.isNotBlank()) pick(
                    "$sec second ka timer shuru kar diya gaya hai.",
                    "$sec सेकंड का टाइमर शुरू कर दिया गया है।",
                    "Timer started for $sec seconds."
                ) else pick("Timer shuru kar diya gaya hai.", "टाइमर शुरू कर दिया गया है।", "Timer started.")
            }
            action == "time" || action == "get_time" -> result.message
            action == "weather" || action == "get_weather" -> pick(
                "Mausam ki jaankari li ja rahi hai...",
                "मौसम की जानकारी प्राप्त की जा रही है...",
                "Fetching the weather..."
            )
            action == "location" || action == "get_current_location" -> {
                val addr = (result.data?.get("address") as? String) ?: ""
                if (addr.isNotBlank()) pick(
                    "Aapki current location hai: $addr",
                    "आपकी वर्तमान लोकेशन है: $addr",
                    "Your current location is: $addr"
                ) else pick("Location mil gayi hai.", "लोकेशन प्राप्त हो गई है।", "Location received.")
            }
            action == "parking" || action == "save_parking" -> pick(
                "Aapki parking location save kar li gayi hai.",
                "आपकी पार्किंग लोकेशन सुरक्षित कर ली गई है।",
                "Parking location saved."
            )
            action == "search_web" -> {
                val query = result.message.removePrefix("Searching for ").trim()
                if (query.isNotBlank()) pick(
                    "$query ke liye search kiya ja raha hai...",
                    "$query के लिए खोजा जा रहा है...",
                    "Searching for $query..."
                ) else pick("Search kiya ja raha hai...", "सर्च किया जा रहा है...", "Searching...")
            }
            action == "browse" || action == "browse_files" -> pick("Files dekh li gayi hain.", "फ़ाइलें देख ली गई हैं।", "Files listed.")
            action == "search_files" || action == "search" -> pick("Files khoj li gayi hain.", "फ़ाइलें खोज ली गई हैं।", "Files found.")
            action == "read" || action == "read_file" -> pick("File padh li gayi hai.", "फ़ाइल पढ़ ली गई है।", "File read.")
            action == "rename" || action == "rename_file" -> pick("File ka naam badal diya gaya hai.", "फ़ाइल का नाम बदल दिया गया है।", "File renamed.")
            action == "copy" || action == "copy_file" -> pick("File copy kar di gayi hai.", "फ़ाइल कॉपी कर दी गई है।", "File copied.")
            action == "move" || action == "move_file" -> pick("File move kar di gayi hai.", "फ़ाइल स्थानांतरित कर दी गई है।", "File moved.")
            action == "delete" || action == "delete_file" -> pick("File hata di gayi hai.", "फ़ाइल हटा दी गई है।", "File deleted.")
            action == "storage" || action == "storage_info" -> pick(
                "Storage ki jaankari mil gayi hai.",
                "स्टोरेज की जानकारी प्राप्त हो गई है।",
                "Storage info received."
            )
            action == "take_photo" || action == "photo" -> pick("Photo le li gayi hai.", "फ़ोटो खींच ली गई है।", "Photo captured.")
            action == "start_recording" || action == "record_video" -> pick(
                "Video recording shuru kar di gayi hai.",
                "वीडियो रिकॉर्डिंग शुरू कर दी गई है।",
                "Video recording started."
            )
            action == "stop_recording" -> pick("Video recording rok di gayi hai.", "वीडियो रिकॉर्डिंग रोक दी गई है।", "Video recording stopped.")
            action == "notifications" || action == "read_notifications" -> {
                val count = Regex("\\d+").find(result.message)?.value ?: "0"
                pick("$count naye notifications mile hain.", "$count नए नोटिफ़िकेशन मिले हैं।", "You have $count new notifications.")
            }
            action == "dismiss" || action == "dismiss_notification" -> pick(
                "Notification hata diya gaya hai.",
                "नोटिफ़िकेशन हटा दिया गया है।",
                "Notification dismissed."
            )
            action == "macro" || action == "create_macro" || action == "run_macro" -> pick(
                "Macro safaltapurvak poora hua.",
                "मैक्रो सफलतापूर्वक पूरा हुआ।",
                "Macro completed successfully."
            )
            action == "back" || action == "press_back" -> pick("Wapas chale gaye hain.", "वापस चले गए हैं।", "Went back.")
            action == "home" || action == "press_home" -> pick("Home screen par chale gaye hain.", "होम स्क्रीन पर चले गए हैं।", "On the home screen.")
            action == "click" || action == "click_element" -> pick("Click kar diya gaya hai.", "क्लिक कर दिया गया है।", "Clicked.")
            action == "type" || action == "type_text" -> pick("Text type kar diya gaya hai.", "टेक्स्ट टाइप कर दिया गया है।", "Text typed.")
            action == "make_call" -> pick("Call lagaya ja raha hai.", "कॉल लगाया जा रहा है।", "Calling now.")
            action == "send_message" || action == "send_sms" -> pick("Message bheja ja raha hai.", "मैसेज भेजा जा रहा है।", "Sending the message.")
            action == "whatsapp" || action == "send_whatsapp_message" -> pick("WhatsApp khola ja raha hai.", "व्हाट्सएप खोला जा रहा है।", "Opening WhatsApp.")
            action == "battery" || action == "get_battery_info" -> {
                val level = Regex("\\d+").find(result.message)?.value ?: ""
                if (level.isNotBlank()) pick(
                    "Battery abhi $level% hai.",
                    "बैटरी अभी $level% है।",
                    "Battery is at $level%."
                ) else pick("Battery ki jaankari mil gayi hai.", "बैटरी की जानकारी प्राप्त हो गई है।", "Battery info received.")
            }
            action == "wifi" || action == "toggle_wifi" -> pick(
                "Wi-Fi settings khol di gayi hain.",
                "वाई-फ़ाई सेटिंग्स खोल दी गई हैं।",
                "Wi-Fi settings opened."
            )
            action == "ringer" || action == "set_ringer_mode" -> pick("Ringer mode badal diya gaya hai.", "रिंगर मोड बदल दिया गया है।", "Ringer mode changed.")
            action == "apps" || action == "list_apps" -> pick(
                "Installed apps ki list mil gayi hai.",
                "इंस्टॉल किए गए ऐप्स की सूची प्राप्त हो गई है।",
                "Installed apps listed."
            )
            else -> if (english || roman) result.message.trim() else translateMessageToHindi(result.message)
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
     * Canonicalizes a generated AI response while preserving the user's script.
     *
     * This is the single choke point used by [AIOrchestrator] for every cloud
     * AI turn. Devanagari forcing is applied ONLY when the user wrote in
     * Devanagari; Roman/English responses pass through untouched so the
     * user's script is never flipped automatically.
     *
     * @param response the raw model text (chat-visible form is preserved)
     * @param userInput the original user turn used for script detection
     */
    fun normalize(response: String, userInput: String = ""): String {
        if (response.isBlank()) return ""

        // Explicit language switch preference always wins.
        if (_preferredLanguage.value == AssistantLanguage.ENGLISH) {
            return response.trim()
        }

        if (userInput.isBlank()) {
            // No signal about the user's script: do NOT force Devanagari.
            // Only clean up system phrases that are already Devanagari-bound
            // when the response itself is Devanagari-heavy.
            return if (LanguageDetector.isDevanagari(response)) {
                translateMessageToHindi(response).trim()
            } else {
                response.trim()
            }
        }

        val detected = LanguageDetector.detect(userInput)
        return when (detected.style) {
            DetectedStyle.DEVANAGARI_HINDI -> translateMessageToHindi(response).trim()
            // Roman Hindi / Hinglish / English / Other: preserve as generated.
            else -> response.trim()
        }
    }

    /**
     * Ensures the final generated response is canonical and clean.
     * Script-preserving: without user-input context it never forces
     * Devanagari onto a Roman/English response.
     */
    fun canonicalize(response: String): String = normalize(response)

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
