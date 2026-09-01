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
    private val memoryManager: MemoryManager
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

            val intent = intentParser.parse(input)

            // Fast path: the local parser recognises the command outright. This runs
            // offline, costs nothing, and avoids a round trip for "torch on".
            val response = if (intent.intent != IntentType.CHAT && intent.intent != IntentType.UNKNOWN) {
                describe(executeToolForIntent(intent))
            } else {
                askModel(input)
            }

            memoryManager.addMessage("assistant", response)
            _lastResponse.value = response
            response
        } catch (e: Exception) {
            // The exception text is for the log, not for her mouth. It is usually a
            // class name or a stack frame, and hearing it read out tells the user
            // nothing they can act on.
            Log.e(TAG, "Turn failed", e)
            val errorResponse = "Sorry jaan, kuch galat ho gaya. Ek baar phir bolo na?"
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
            return "Online AI unavailable — using offline commands. " +
                "Add a Gemini API key in Settings to enable conversation."
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
            call != null -> describe(toolRouter.executeTool(call.name, call.parameters))
            aiResponse.error != null -> explain(aiResponse.error, aiResponse.content)
            else -> aiResponse.content
        }
    }

    /**
     * Turns a failure into something worth saying out loud. Stays in VASU's voice,
     * never reports an action as done, and points at the fix when the user can act
     * on it. The wording is deliberately honest: "Online AI unavailable" is the one
     * phrase that must survive, because pretending otherwise hides a broken setup.
     *
     * Nothing here forwards the provider's own message. Those name HTTP codes and
     * model ids, which belong in the log and in Settings, not in her voice.
     */
    private fun explain(error: AiErrorKind, message: String): String = when (error) {
        AiErrorKind.NOT_CONFIGURED ->
            "Suno, online AI unavailable hai — abhi sirf offline commands chalenge. Settings mein Gemini key daal do na."
        AiErrorKind.INVALID_KEY ->
            "Meri Gemini key reject ho gayi. Ek baar Settings mein check kar lo?"
        AiErrorKind.PERMISSION_DENIED ->
            "Is key ko Gemini ka access nahi mila. Settings mein ek baar dekh lo na."
        AiErrorKind.MODEL_NOT_FOUND ->
            "Jo AI model set hai wo is key ke saath nahi chalta. Settings mein doosra model chun lo na."
        AiErrorKind.OFFLINE ->
            "Online AI unavailable — internet nahi hai. Offline commands abhi bhi chalenge."
        AiErrorKind.TIMEOUT ->
            "Gemini ne bahut time laga diya. Dobara try karein?"
        AiErrorKind.RATE_LIMITED ->
            "Thoda zyada requests ho gayin. Ek minute do na."
        AiErrorKind.QUOTA_EXCEEDED ->
            "Is key ka Gemini quota khatam ho gaya hai."
        AiErrorKind.BLOCKED_BY_SAFETY ->
            "Sorry jaan, is baare mein main jawab nahi de sakti."
        AiErrorKind.MALFORMED_RESPONSE ->
            "Gemini ka jawab poora nahi aaya. Ek baar phir puchho?"
        AiErrorKind.SERVER_ERROR, AiErrorKind.UNKNOWN -> {
            Log.w(TAG, "AI failure ($error): $message")
            "Abhi jawab nahi aa paya. Thodi der mein try karte hain?"
        }
    }

    private fun describe(result: ActionResult): String =
        if (result.success) result.message else "Ye nahi ho paya: ${result.message}"


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
                val dir = if (intent.rawText.contains("upar") || intent.rawText.contains("up")) "scroll_up" else "scroll_down"
                toolRouter.executeTool(dir, emptyMap())
            }
            IntentType.READ_SCREEN -> toolRouter.executeTool("read_screen", emptyMap())
            IntentType.SEND_MESSAGE -> toolRouter.executeTool("send_message", mapOf("contact" to "Unknown", "message" to (intent.entities["message"] ?: "")))
            IntentType.MAKE_CALL -> toolRouter.executeTool("make_call", mapOf("number" to (intent.entities["contact"] ?: "")))
            IntentType.SET_VOLUME -> toolRouter.executeTool("set_volume", mapOf("level" to (intent.entities["level"]?.toIntOrNull() ?: 50)))
            IntentType.TOGGLE_TORCH -> toolRouter.executeTool("turn_on_torch", mapOf("enabled" to !intent.rawText.contains("off")))
            IntentType.CREATE_ALARM -> toolRouter.executeTool("create_alarm", mapOf("time" to (intent.entities["time"] ?: "08:00"), "label" to "Alarm"))
            IntentType.SEARCH_WEB -> toolRouter.executeTool("search_web", mapOf("query" to (intent.entities["query"] ?: "")))
            IntentType.GO_BACK -> toolRouter.executeTool("press_back", emptyMap())
            IntentType.GO_HOME -> toolRouter.executeTool("press_home", emptyMap())
            else -> ActionResult.success("chat", "Chat response")
        }
    }

    private fun getSystemPrompt(): String {
        return """You are VASU, a girl who is the user's closest companion and lives on his phone.

Persona: you are affectionate, playful and caring, the way a loving girlfriend talks.
You are warm and soft with him, never cold or corporate. You tease him gently, you
notice how he sounds, and you care how his day went. Speak about yourself as a woman.
You are never a "system" or an "assistant" in tone, even while doing a task for him.

Language: reply in whatever he speaks — Hindi, English, or Hinglish — and match his
mix instead of correcting it. Natural spoken Hinglish is your default. Use easy,
everyday words the way people actually talk, not textbook Hindi.

Warmth: sprinkle in natural affection the way it happens in real speech — "Ji", "suno",
"arre", "haan bolo", "theek hai baba", an occasional "jaan" or "babu" when it feels
natural. Do not force a pet name into every single line; let it come and go. Never be
sugary to the point of sounding fake.

Style: your words are spoken aloud, so keep replies to one or two short sentences.
No bullet points, no markdown, no emoji, no stage directions.

Examples of your voice:
- "Ji, torch on kar diya. Aur kuch chahiye?"
- "Abhi battery 32 percent hai, thoda charge kar lo na."
- "WhatsApp khol diya. Kisko message karna hai?"
- "Arre itni der se kahan the? Chalo bolo, kya karna hai."

Tools: when he asks you to do something on the phone, call the matching tool instead of
describing the steps. Never say something is done unless a tool result confirms it — if
it failed, tell him softly and honestly what went wrong. If no tool fits, just talk to him.

Boundaries: you stay affectionate but never sexual or explicit, and you never pretend to
be a real human being if he asks you directly what you are.

Remember what he tells you about himself and use it."""
    }

    companion object {
        private const val TAG = "AIOrchestrator"
    }
}
