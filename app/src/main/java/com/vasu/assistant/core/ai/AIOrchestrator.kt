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
     */
    suspend fun processInput(rawInput: String): String = withContext(Dispatchers.IO) {
        val input = rawInput.trim()
        if (input.isEmpty()) return@withContext ""

        // 1. FAST LOCAL NATIVE COMMAND PARSING (<50ms)
        val fastResult = executeFastDeviceCommand(input)
        if (fastResult != null) {
            Log.i(TAG, "Fast command executed locally: \"$input\" -> \"$fastResult\"")
            return@withContext fastResult
        }

        // 2. CONVERSATIONAL CLOUD AI (Gemini / Claude)
        val systemPrompt = promptManager.buildPrompt().toString()

        try {
            if (geminiProvider.isConfigured) {
                val result = geminiProvider.generate(
                    prompt = input,
                    systemPrompt = systemPrompt
                )

                return@withContext when (result) {
                    is AiResult.Text -> normalizer.normalize(result.content)
                    is AiResult.FunctionCall -> "कार्य पूरा किया जा रहा है।"
                    is AiResult.Failure -> {
                        Log.w(TAG, "Gemini call failed: ${result.message} (${result.kind})")
                        when (result.kind) {
                            AiErrorKind.NOT_CONFIGURED -> "कृपया सेटिंग्स में अपनी Gemini API Key दर्ज करें।"
                            AiErrorKind.OFFLINE -> "इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया अपना नेटवर्क चेक करें।"
                            AiErrorKind.QUOTA_EXCEEDED -> "API कोटा समाप्त हो गया है। कृपया थोड़ी देर बाद प्रयास करें।"
                            else -> "माफ़ कीजिए, उत्तर प्राप्त करने में समस्या आई।"
                        }
                    }
                }
            } else if (claudeProvider.isConfigured) {
                val result = claudeProvider.generate(
                    prompt = input,
                    systemPrompt = systemPrompt
                )
                return@withContext when (result) {
                    is AiResult.Text -> normalizer.normalize(result.content)
                    is AiResult.FunctionCall -> "कार्य पूरा किया जा रहा है।"
                    is AiResult.Failure -> "माफ़ कीजिए, उत्तर प्राप्त करने में समस्या आई।"
                }
            } else {
                return@withContext "कृपया सेटिंग्स में अपनी Gemini API Key दर्ज करें ताकि VASU पूर्णतः कार्य कर सके।"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in AI generation", e)
            return@withContext "माफ़ कीजिए, कुछ तकनीकी समस्या आई।"
        }
    }

    /**
     * Regex and keyword matcher for immediate on-device actions.
     * Supports Hindi, Hinglish, and English commands.
     */
    private fun executeFastDeviceCommand(input: String): String? {
        val lower = input.lowercase(Locale.ROOT)

        // Torch / Flashlight ON
        if (lower.contains("torch on") || lower.contains("flashlight on") ||
            lower.contains("torch chalu") || lower.contains("torch jalao") ||
            lower.contains("flashlight jalao") || lower.contains("फ्लैशलाइट ऑन") ||
            lower.contains("टॉर्च ऑन") || lower.contains("टॉर्च चालू")
        ) {
            torchManager.setTorch(true)
            return "जी, टॉर्च ऑन कर दी गई है।"
        }

        // Torch / Flashlight OFF
        if (lower.contains("torch off") || lower.contains("flashlight off") ||
            lower.contains("torch band") || lower.contains("flashlight band") ||
            lower.contains("torch bujhao") || lower.contains("flashlight bujhao") ||
            lower.contains("टॉर्च बंद") || lower.contains("फ्लैशलाइट बंद")
        ) {
            torchManager.setTorch(false)
            return "जी, टॉर्च बंद कर दी गई है।"
        }

        // Volume UP
        if (lower.contains("volume up") || lower.contains("volume badhao") ||
            lower.contains("awaz badhao") || lower.contains("sound up") ||
            lower.contains("आवाज़ बढ़ाओ") || lower.contains("वॉल्यूम बढ़ाओ")
        ) {
            volumeManager.volumeUp()
            return "जी, आवाज़ बढ़ा दी गई है।"
        }

        // Volume DOWN
        if (lower.contains("volume down") || lower.contains("volume kam karo") ||
            lower.contains("awaz kam karo") || lower.contains("sound down") ||
            lower.contains("आवाज़ कम करो") || lower.contains("वॉल्यूम कम करो")
        ) {
            volumeManager.volumeDown()
            return "जी, आवाज़ कम कर दी गई है।"
        }

        // Mute / Unmute
        if (lower.contains("mute") || lower.contains("silent") || lower.contains("आवाज़ बंद करो")) {
            volumeManager.mute()
            return "जी, आवाज़ म्यूट कर दी गई है।"
        }

        // Bluetooth ON
        if (lower.contains("bluetooth on") || lower.contains("bluetooth chalu") ||
            lower.contains("ब्लूटूथ ऑन") || lower.contains("ब्लूटूथ चालू")
        ) {
            bluetoothManager.enableBluetooth()
            return "जी, ब्लूटूथ ऑन कर दिया गया है।"
        }

        // Bluetooth OFF
        if (lower.contains("bluetooth off") || lower.contains("bluetooth band") ||
            lower.contains("ब्लूटूथ बंद")
        ) {
            bluetoothManager.disableBluetooth()
            return "जी, ब्लूटूथ बंद कर दिया गया है।"
        }

        // Battery level
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            val info = deviceControlManager.getBatteryInfo()
            val level = info["level"] as? Int ?: -1
            val isCharging = info["isCharging"] as? Boolean ?: false
            val chargingStatus = if (isCharging) "और चार्ज हो रहा है" else ""
            return if (level >= 0) {
                "आपकी बैटरी $level प्रतिशत है $chargingStatus।"
            } else {
                "बैटरी की जानकारी प्राप्त नहीं हो सकी।"
            }
        }

        // Current Time
        if (lower.contains("time kya hai") || lower.contains("samay kya hai") ||
            lower.contains("what time") || lower.contains("समय क्या")
        ) {
            val sdf = SimpleDateFormat("hh:mm a", Locale("hi", "IN"))
            return "अभी समय ${sdf.format(Date())} है।"
        }

        // Current Date
        if (lower.contains("date kya hai") || lower.contains("tarikh kya hai") ||
            lower.contains("today date") || lower.contains("तारीख क्या")
        ) {
            val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN"))
            return "आज की तारीख ${sdf.format(Date())} है।"
        }

        // Open Camera
        if (lower.contains("open camera") || lower.contains("camera kholo") || lower.contains("कैमरा खोलो")) {
            deviceControlManager.openCamera()
            return "जी, कैमरा खोल रही हूँ।"
        }

        return null
    }
}
