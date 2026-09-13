package com.vasu.assistant.core.ai

import android.content.Context
import android.util.Log
import com.vasu.assistant.devices.BluetoothManager
import com.vasu.assistant.devices.DeviceControlManager
import com.vasu.assistant.devices.TorchManager
import com.vasu.assistant.devices.VolumeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIOrchestrator - Central routing engine for all user commands and voice interactions.
 *
 * Directs:
 * 1. Native Fast Path (<50ms execution on-device): Torch, Volume, Bluetooth, Battery, Time, Alarm
 * 2. Conversational AI Path: GeminiProvider with Hindi/Hinglish prompt tuning and HindiResponseNormalizer
 */
@Singleton
class AIOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiProvider: GeminiProvider,
    private val claudeProvider: ClaudeProvider,
    private val promptManager: PromptManager,
    private val normalizer: HindiResponseNormalizer,
    private val torchManager: TorchManager,
    private val volumeManager: VolumeManager,
    private val bluetoothManager: BluetoothManager,
    private val deviceControlManager: DeviceControlManager
) {
    companion object {
        private const val TAG = "AIOrchestrator"
    }

    /**
     * Process arbitrary user text/voice input, executing locally if it matches device commands,
     * or routing to Gemini AI for natural conversational responses.
     *
     * LANGUAGE CONTRACT: every turn is classified by [LanguageDetector] and the
     * detected style is injected into the AI request, so the reply mirrors the
     * user's script (Roman -> Roman, Devanagari -> Devanagari, English ->
     * English). Fast local confirmations are script-aware as well.
     *
     * VOICE CONTRACT: the returned string is the single canonical assistant
     * response for this turn — callers issue exactly ONE TTS request for it
     * after the response is complete (never per stream chunk).
     */
    suspend fun processInput(rawInput: String): String = withContext(Dispatchers.IO) {
        val input = rawInput.trim()
        if (input.isEmpty()) return@withContext ""

        val requestId = "req_${System.currentTimeMillis()}_${input.hashCode()}"
        val detected = LanguageDetector.detect(input)
        Log.i(TAG, "LANG requestId=$requestId style=${detected.style} script=${detected.script}")

        // 0. EXPLICIT LANGUAGE SWITCH + FAST CONVERSATIONAL REPLIES (script-aware)
        val switchReply = normalizer.checkLanguageSwitchCommand(input)
        if (switchReply != null) {
            Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=language_switch style=${detected.style}")
            return@withContext switchReply
        }
        val conversational = normalizer.getConversationalResponse(input)
        if (conversational != null) {
            Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=conversational style=${detected.style}")
            return@withContext conversational
        }

        // 1. FAST LOCAL NATIVE COMMAND PARSING (<50ms)
        val fastResult = executeFastDeviceCommand(input, detected)
        if (fastResult != null) {
            Log.i(TAG, "Fast command executed locally: requestId=$requestId style=${detected.style}")
            Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=fast_device style=${detected.style}")
            return@withContext fastResult
        }

        // 2. CONVERSATIONAL CLOUD AI (Gemini / Claude)
        val systemPrompt = promptManager.buildPrompt(userInput = input).toString()

        try {
            if (geminiProvider.isConfigured) {
                val result = geminiProvider.generate(
                    prompt = input,
                    systemPrompt = systemPrompt
                )

                return@withContext when (result) {
                    is AiResult.Text -> {
                        val canonical = normalizer.normalize(result.content, input)
                        Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=gemini style=${detected.style}")
                        canonical
                    }
                    is AiResult.FunctionCall -> scriptPick(
                        detected,
                        roman = "Kaam poora kiya ja raha hai.",
                        deva = "कार्य पूरा किया जा रहा है।",
                        eng = "Working on it."
                    ).also {
                        Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=function_call style=${detected.style}")
                    }
                    is AiResult.Failure -> {
                        Log.w(TAG, "Gemini call failed: ${result.message} (${result.kind})")
                        geminiFailureMessage(result.kind, detected).also {
                            Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=gemini_error style=${detected.style}")
                        }
                    }
                }
            } else if (claudeProvider.isConfigured) {
                val result = claudeProvider.generate(
                    prompt = input,
                    systemPrompt = systemPrompt
                )
                return@withContext when (result) {
                    is AiResult.Text -> {
                        val canonical = normalizer.normalize(result.content, input)
                        Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=claude style=${detected.style}")
                        canonical
                    }
                    is AiResult.FunctionCall -> scriptPick(
                        detected,
                        roman = "Kaam poora kiya ja raha hai.",
                        deva = "कार्य पूरा किया जा रहा है।",
                        eng = "Working on it."
                    ).also {
                        Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=function_call style=${detected.style}")
                    }
                    is AiResult.Failure -> scriptPick(
                        detected,
                        roman = "Maaf kijiye, jawab milne mein samasya aayi.",
                        deva = "माफ़ कीजिए, उत्तर प्राप्त करने में समस्या आई।",
                        eng = "Sorry, I could not get an answer."
                    ).also {
                        Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=claude_error style=${detected.style}")
                    }
                }
            } else {
                return@withContext scriptPick(
                    detected,
                    roman = "Kripya Settings mein apni Gemini API Key darj karein taaki VASU poori tarah kaam kar sake.",
                    deva = "कृपया सेटिंग्स में अपनी Gemini API Key दर्ज करें ताकि VASU पूर्णतः कार्य कर सके।",
                    eng = "Please add your Gemini API key in Settings so VASU can work fully."
                ).also {
                    Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=not_configured style=${detected.style}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in AI generation", e)
            return@withContext scriptPick(
                detected,
                roman = "Maaf kijiye, kuch takneeki samasya aayi.",
                deva = "माफ़ कीजिए, कुछ तकनीकी समस्या आई।",
                eng = "Sorry, something went wrong."
            ).also {
                Log.i(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId source=exception style=${detected.style}")
            }
        }
    }

    /**
     * Picks a script-appropriate string for a detected user style.
     * OTHER (empty/unclassifiable) keeps the legacy Devanagari default so
     * offline/error paths behave exactly as before when there is no user
     * script signal at all.
     */
    private fun scriptPick(detected: DetectedLanguage, roman: String, deva: String, eng: String): String =
        when (detected.style) {
            DetectedStyle.ENGLISH -> eng
            DetectedStyle.ROMAN_HINDI, DetectedStyle.HINGLISH -> roman
            DetectedStyle.DEVANAGARI_HINDI, DetectedStyle.OTHER -> deva
        }

    /** Script-aware cloud-error messages (never force Devanagari on Roman/English users). */
    private fun geminiFailureMessage(kind: AiErrorKind, detected: DetectedLanguage): String =
        when (kind) {
            AiErrorKind.NOT_CONFIGURED -> scriptPick(
                detected,
                roman = "Kripya Settings mein apni Gemini API Key darj karein.",
                deva = "कृपया सेटिंग्स में अपनी Gemini API Key दर्ज करें।",
                eng = "Please add your Gemini API key in Settings."
            )
            AiErrorKind.OFFLINE -> scriptPick(
                detected,
                roman = "Internet connection uplabdh nahi hai. Kripya apna network check karein.",
                deva = "इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया अपना नेटवर्क चेक करें।",
                eng = "No internet connection. Please check your network."
            )
            AiErrorKind.QUOTA_EXCEEDED -> scriptPick(
                detected,
                roman = "API quota khatm ho gaya hai. Kripya thodi der baad prayas karein.",
                deva = "API कोटा समाप्त हो गया है। कृपया थोड़ी देर बाद प्रयास करें।",
                eng = "API quota exhausted. Please try again later."
            )
            else -> scriptPick(
                detected,
                roman = "Maaf kijiye, jawab prapt karne mein samasya aayi.",
                deva = "माफ़ कीजिए, उत्तर प्राप्त करने में समस्या आई।",
                eng = "Sorry, I could not get an answer."
            )
        }

    /**
     * Regex and keyword matcher for immediate on-device actions.
     * Supports Hindi, Hinglish, and English commands.
     * Confirmations mirror the user's script via [scriptPick].
     */
    private fun executeFastDeviceCommand(input: String, detected: DetectedLanguage): String? {
        val lower = input.lowercase(Locale.ROOT)

        // Torch / Flashlight ON
        if (lower.contains("torch on") || lower.contains("flashlight on") ||
            lower.contains("torch chalu") || lower.contains("torch jalao") ||
            lower.contains("flashlight jalao") || lower.contains("फ्लैशलाइट ऑन") ||
            lower.contains("टॉर्च ऑन") || lower.contains("टॉर्च चालू")
        ) {
            torchManager.setTorch(true)
            return scriptPick(detected, "Ji, torch on kar di gayi hai.", "जी, टॉर्च ऑन कर दी गई है।", "Torch is now on.")
        }

        // Torch / Flashlight OFF
        if (lower.contains("torch off") || lower.contains("flashlight off") ||
            lower.contains("torch band") || lower.contains("flashlight band") ||
            lower.contains("torch bujhao") || lower.contains("flashlight bujhao") ||
            lower.contains("टॉर्च बंद") || lower.contains("फ्लैशलाइट बंद")
        ) {
            torchManager.setTorch(false)
            return scriptPick(detected, "Ji, torch band kar di gayi hai.", "जी, टॉर्च बंद कर दी गई है।", "Torch is now off.")
        }

        // Volume UP
        if (lower.contains("volume up") || lower.contains("volume badhao") ||
            lower.contains("awaz badhao") || lower.contains("sound up") ||
            lower.contains("आवाज़ बढ़ाओ") || lower.contains("वॉल्यूम बढ़ाओ")
        ) {
            volumeManager.volumeUp()
            return scriptPick(detected, "Ji, awaaz badha di gayi hai.", "जी, आवाज़ बढ़ा दी गई है।", "Volume increased.")
        }

        // Volume DOWN
        if (lower.contains("volume down") || lower.contains("volume kam karo") ||
            lower.contains("awaz kam karo") || lower.contains("sound down") ||
            lower.contains("आवाज़ कम करो") || lower.contains("वॉल्यूम कम करो")
        ) {
            volumeManager.volumeDown()
            return scriptPick(detected, "Ji, awaaz kam kar di gayi hai.", "जी, आवाज़ कम कर दी गई है।", "Volume decreased.")
        }

        // Mute / Unmute
        if (lower.contains("mute") || lower.contains("silent") || lower.contains("आवाज़ बंद करो")) {
            volumeManager.mute()
            return scriptPick(detected, "Ji, awaaz mute kar di gayi hai.", "जी, आवाज़ म्यूट कर दी गई है।", "Sound muted.")
        }

        // Bluetooth ON
        if (lower.contains("bluetooth on") || lower.contains("bluetooth chalu") ||
            lower.contains("ब्लूटूथ ऑन") || lower.contains("ब्लूटूथ चालू")
        ) {
            bluetoothManager.enableBluetooth()
            return scriptPick(detected, "Ji, Bluetooth on kar diya gaya hai.", "जी, ब्लूटूथ ऑन कर दिया गया है।", "Bluetooth is now on.")
        }

        // Bluetooth OFF
        if (lower.contains("bluetooth off") || lower.contains("bluetooth band") ||
            lower.contains("ब्लूटूथ बंद")
        ) {
            bluetoothManager.disableBluetooth()
            return scriptPick(detected, "Ji, Bluetooth band kar diya gaya hai.", "जी, ब्लूटूथ बंद कर दिया गया है।", "Bluetooth is now off.")
        }

        // Battery level
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            val info = deviceControlManager.getBatteryInfo()
            val level = info["level"] as? Int ?: -1
            val isCharging = info["isCharging"] as? Boolean ?: false
            val chargingStatus = if (isCharging) "और चार्ज हो रहा है" else ""
            val chargingRoman = if (isCharging) "aur charge ho raha hai" else ""
            val chargingEng = if (isCharging) "and charging" else ""
            return if (level >= 0) {
                scriptPick(
                    detected,
                    "Aapki battery $level pratishat hai $chargingRoman.",
                    "आपकी बैटरी $level प्रतिशत है $chargingStatus।",
                    "Your battery is at $level% $chargingEng."
                )
            } else {
                scriptPick(
                    detected,
                    "Battery ki jaankari prapt nahi ho saki.",
                    "बैटरी की जानकारी प्राप्त नहीं हो सकी।",
                    "Could not read the battery level."
                )
            }
        }

        // Current Time
        if (lower.contains("time kya hai") || lower.contains("samay kya hai") ||
            lower.contains("what time") || lower.contains("समय क्या")
        ) {
            val sdf = SimpleDateFormat("hh:mm a", Locale("hi", "IN"))
            val t = sdf.format(Date())
            return scriptPick(detected, "Abhi samay $t hai.", "अभी समय $t है।", "The time is $t.")
        }

        // Current Date
        if (lower.contains("date kya hai") || lower.contains("tarikh kya hai") ||
            lower.contains("today date") || lower.contains("तारीख क्या")
        ) {
            val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN"))
            val d = sdf.format(Date())
            return scriptPick(detected, "Aaj ki tarikh $d hai.", "आज की तारीख $d है।", "Today is $d.")
        }

        // Open Camera
        if (lower.contains("open camera") || lower.contains("camera kholo") || lower.contains("कैमरा खोलो")) {
            deviceControlManager.openCamera()
            return scriptPick(detected, "Ji, camera khol rahi hoon.", "जी, कैमरा खोल रही हूँ।", "Opening the camera.")
        }

        return null
    }
}
