package com.vasu.assistant.core.ai

import android.util.Log
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.memory.MemoryManager
import com.vasu.assistant.core.tts.TTSManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIOrchestrator @Inject constructor(
    private val aiClient: AIClient,
    private val intentParser: IntentParser,
    private val toolRouter: ToolRouter,
    private val ttsManager: TTSManager,
    private val memoryManager: MemoryManager,
    private val normalizer: HindiResponseNormalizer
) {
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse.asStateFlow()

    suspend fun processInput(input: String): String {
        _isProcessing.value = true

        return try {
            memoryManager.addMessage("user", input)
            memoryManager.processInput(input)

            // 1. Language switch command check (e.g. "reply in English", "reply in Hindi")
            val switchMessage = normalizer.checkLanguageSwitchCommand(input)
            if (switchMessage != null) {
                memoryManager.addMessage("assistant", switchMessage)
                _lastResponse.value = switchMessage
                return switchMessage
            }

            val intent = intentParser.parse(input)

            // 2. Fast path: device action commands or offline conversational responses
            val rawResponse = when {
                intent.intent != IntentType.CHAT && intent.intent != IntentType.UNKNOWN -> {
                    val result = executeToolForIntent(intent)
                    normalizer.describeActionResult(result, input)
                }
                else -> {
                    val conversational = normalizer.getConversationalResponse(input)
                    if (conversational != null && !aiClient.isCloudReady) {
                        conversational
                    } else {
                        askModel(input)
                    }
                }
            }

            // 3. Single canonical response normalizer (exact same text for UI and TTS)
            val canonicalResponse = normalizer.canonicalize(rawResponse)

            memoryManager.addMessage("assistant", canonicalResponse)
            _lastResponse.value = canonicalResponse
            canonicalResponse
        } catch (e: Exception) {
            Log.e(TAG, "Turn failed", e)
            val errorResponse = "माफ़ कीजिए, कुछ गड़बड़ हो गई। कृपया एक बार फिर से बोलिए ना?"
            _lastResponse.value = errorResponse
            errorResponse
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Sends the turn to Gemini with the tool registry attached. If the model picks
     * a tool we execute it through ToolRouter, which re-validates the name and
     * applies the permission gate, so a hallucinated tool fails closed.
     */
    private suspend fun askModel(input: String): String {
        if (!aiClient.isCloudReady) {
            val quick = normalizer.getConversationalResponse(input)
            if (quick != null) return quick

            return "ऑनलाइन एआई उपलब्ध नहीं है — अभी केवल ऑफ़लाइन कमांड काम करेंगे। " +
                "बातचीत शुरू करने के लिए सेटिंग्स में जेमिनी एपीआई की (API Key) जोड़ें।"
        }

        val history = memoryManager.getFullContext()
        val systemPrompt = getSystemPrompt() + if (history.isNotBlank()) "\n\n$history" else ""

        val aiResponse = aiClient.chatWithTools(
            messages = listOf(ChatMessage("user", input)),
            tools = toolRouter.getAvailableTools(),
            systemPrompt = systemPrompt
        )

        val call = aiResponse.toolCalls.firstOrNull()
        return when {
            call != null -> normalizer.describeActionResult(toolRouter.executeTool(call.name, call.parameters), input)
            aiResponse.error != null -> explain(aiResponse.error, aiResponse.content)
            else -> aiResponse.content
        }
    }

    /**
     * Turns a failure into natural Hindi Devanagari output.
     */
    private fun explain(error: AiErrorKind, message: String): String = when (error) {
        AiErrorKind.NOT_CONFIGURED ->
            "ऑनलाइन एआई उपलब्ध नहीं है — अभी केवल ऑफ़लाइन कमांड काम करेंगे। सेटिंग्स में जेमिनी एपीआई की जोड़ें।"
        AiErrorKind.INVALID_KEY ->
            "जेमिनी एपीआई की अमान्य है। कृपया सेटिंग्स में जाकर चेक करें।"
        AiErrorKind.PERMISSION_DENIED ->
            "इस एपीआई की को जेमिनी का एक्सेस नहीं मिला। कृपया सेटिंग्स में देखें।"
        AiErrorKind.MODEL_NOT_FOUND ->
            "चुना गया एआई मॉडल उपलब्ध नहीं है। कृपया सेटिंग्स में दूसरा मॉडल चुनें।"
        AiErrorKind.OFFLINE ->
            "इंटरनेट कनेक्शन नहीं है — केवल ऑफ़लाइन कमांड काम करेंगे।"
        AiErrorKind.TIMEOUT ->
            "जवाब आने में बहुत समय लग रहा है। क्या हम दोबारा कोशिश करें?"
        AiErrorKind.RATE_LIMITED ->
            "बहुत सारे अनुरोध हो गए हैं। कृपया एक मिनट रुकें।"
        AiErrorKind.QUOTA_EXCEEDED ->
            "जेमिनी एपीआई कोटा समाप्त हो गया है।"
        AiErrorKind.BLOCKED_BY_SAFETY ->
            "माफ़ कीजिए, मैं इस बारे में जवाब नहीं दे सकती।"
        AiErrorKind.MALFORMED_RESPONSE ->
            "पूरा जवाब प्राप्त नहीं हो सका। कृपया दोबारा पूछें।"
        AiErrorKind.SERVER_ERROR, AiErrorKind.UNKNOWN -> {
            Log.w(TAG, "AI failure ($error): $message")
            "अभी जवाब नहीं मिल सका। थोड़ी देर में दोबारा कोशिश करते हैं।"
        }
    }

    private fun describe(result: ActionResult): String =
        normalizer.describeActionResult(result)

    suspend fun processVoiceInput(transcript: String): String = processInput(transcript)
    fun speakResponse(text: String) { ttsManager.speakQueued(text) }
    fun stop() { _isProcessing.value = false; ttsManager.stop() }

    private suspend fun executeToolForIntent(intent: ParsedIntent): ActionResult {
        return when (intent.intent) {
            IntentType.OPEN_APP -> {
                val pkg = intent.entities["package"] ?: ""
                toolRouter.executeTool("open_app", mapOf("package" to pkg))
            }
            IntentType.CLICK -> toolRouter.executeTool("click", mapOf("text" to (intent.entities["text"] ?: "")))
            IntentType.TYPE_TEXT -> toolRouter.executeTool("type_text", mapOf("text" to (intent.entities["text"] ?: ""), "label" to ""))
            IntentType.SCROLL -> {
                val dir = if (intent.rawText.contains("upar") || intent.rawText.contains("up") || intent.rawText.contains("ऊपर")) "scroll_up" else "scroll_down"
                toolRouter.executeTool(dir, emptyMap())
            }
            IntentType.READ_SCREEN -> toolRouter.executeTool("read_screen", emptyMap())
            IntentType.SEND_MESSAGE -> toolRouter.executeTool("send_message", mapOf("contact" to "Unknown", "message" to (intent.entities["message"] ?: "")))
            IntentType.MAKE_CALL -> toolRouter.executeTool("make_call", mapOf("number" to (intent.entities["contact"] ?: "")))
            IntentType.SET_VOLUME -> toolRouter.executeTool("set_volume", mapOf("level" to (intent.entities["level"]?.toIntOrNull() ?: 50)))
            IntentType.TOGGLE_TORCH -> {
                val isOff = intent.rawText.contains("off", ignoreCase = true) ||
                    intent.rawText.contains("band", ignoreCase = true) ||
                    intent.rawText.contains("बंद")
                toolRouter.executeTool("turn_on_torch", mapOf("enabled" to !isOff))
            }
            IntentType.TOGGLE_BLUETOOTH -> toolRouter.executeTool("toggle_bluetooth", emptyMap())
            IntentType.GET_BATTERY -> toolRouter.executeTool("battery_info", emptyMap())
            IntentType.MEDIA_CONTROL -> {
                val action = when {
                    intent.rawText.contains("next") || intent.rawText.contains("अगला") -> "media_next"
                    intent.rawText.contains("previous") || intent.rawText.contains("पिछला") -> "media_previous"
                    intent.rawText.contains("pause") || intent.rawText.contains("रोक") -> "pause_music"
                    else -> "play_music"
                }
                toolRouter.executeTool(action, emptyMap())
            }
            IntentType.SET_TIMER -> toolRouter.executeTool("set_timer", mapOf("seconds" to (intent.entities["level"]?.toIntOrNull() ?: 60), "label" to "Timer"))
            IntentType.MANAGE_FILES -> toolRouter.executeTool("browse_files", emptyMap())
            IntentType.TAKE_PHOTO -> toolRouter.executeTool("take_photo", emptyMap())
            IntentType.GET_LOCATION -> toolRouter.executeTool("get_current_location", emptyMap())
            IntentType.CREATE_ALARM -> toolRouter.executeTool("create_alarm", mapOf("time" to (intent.entities["time"] ?: "08:00"), "label" to "Alarm"))
            IntentType.SEARCH_WEB -> toolRouter.executeTool("search_web", mapOf("query" to (intent.entities["query"] ?: "")))
            IntentType.GET_TIME -> toolRouter.executeTool("get_time", emptyMap())
            IntentType.GET_WEATHER -> toolRouter.executeTool("get_weather", emptyMap())
            IntentType.GO_BACK -> toolRouter.executeTool("press_back", emptyMap())
            IntentType.GO_HOME -> toolRouter.executeTool("press_home", emptyMap())
            else -> ActionResult.success("chat", "Chat response")
        }
    }

    private fun getSystemPrompt(): String {
        return """तुम VASU हो। सामान्य बातचीत में हमेशा स्वाभाविक, बोलचाल की हिंदी देवनागरी लिपि में उत्तर दो। रोमन हिंदी जैसे 'kya haal hai', 'main theek hoon' का उपयोग मत करो। उत्तर ऐसे लिखो जैसे कोई भारतीय व्यक्ति स्वाभाविक रूप से बोल रहा हो।

मुख्य नियम:
1. भाषा नीति: डिफ़ॉल्ट उत्तर हमेशा शुद्ध एवं स्वाभाविक हिंदी देवनागरी लिपि में होना चाहिए। जब तक उपयोगकर्ता विशेष रूप से अंग्रेजी में बात करने को न कहे, रोमन हिंदी (Hinglish) में उत्तर कभी मत दो।
2. तकनीकी शब्द: अंग्रेजी के तकनीकी शब्द केवल तब रखें जब उनका हिंदी विकल्प अस्वाभाविक या अस्पष्ट हो (जैसे 'फोन', 'अलार्म', 'वॉल्यूम', 'ऐप')।
3. आत्मीयता एवं शैली: तुम्हारी शैली एक स्नेही, सहायक और आत्मीय भारतीय AI साथी की है। उत्तर संक्षिप्त (1-2 वाक्य) और सीधा रखो क्योंकि इसे सीधे स्पीच इंजन (TTS) द्वारा बोला जाएगा।
4. फ़ोन कमांड्स: जब उपयोगकर्ता फ़ोन पर कोई कार्य करने को कहे (जैसे टॉर्च चालू करना, आवाज़ बढ़ाना, ऐप खोलना), तो उचित टूल का उपयोग करो। जब तक टूल सफल न हो, कार्य पूरा होने का दावा मत करो।"""
    }

    companion object {
        private const val TAG = "AIOrchestrator"
    }
}
