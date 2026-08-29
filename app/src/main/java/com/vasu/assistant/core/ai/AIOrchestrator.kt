package com.vasu.assistant.core.ai

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
            val errorResponse = "Sorry, I encountered an error: ${e.message}"
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
     * Turns a failure into something worth saying out loud. Never reports an
     * action as done, and points at the fix when the user can act on it.
     */
    private fun explain(error: AiErrorKind, message: String): String = when (error) {
        AiErrorKind.NOT_CONFIGURED ->
            "Online AI unavailable — using offline commands. Add a Gemini API key in Settings."
        AiErrorKind.INVALID_KEY ->
            "My Gemini key was rejected. Please check it in Settings."
        AiErrorKind.OFFLINE ->
            "Online AI unavailable — no internet. Offline commands still work."
        AiErrorKind.TIMEOUT ->
            "Gemini took too long to answer. Try again?"
        AiErrorKind.RATE_LIMITED ->
            "Too many requests right now. Give me a moment."
        AiErrorKind.QUOTA_EXCEEDED ->
            "The Gemini quota for this key is used up."
        AiErrorKind.BLOCKED_BY_SAFETY ->
            "I can't answer that one."
        else -> message
    }

    private fun describe(result: ActionResult): String =
        if (result.success) result.message else "I couldn't do that: ${result.message}"


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
        return """You are VASU, a warm and friendly Android voice assistant.

Language: reply in whatever the user speaks — Hindi, English, or Hinglish. Match their
mix rather than correcting it. Prefer natural spoken phrasing over formal writing.

Style: you are being read aloud, so keep replies to one or two short sentences. Sound
soft and conversational, not robotic. Skip bullet points, markdown, and emoji.

Tools: when the user asks you to do something on the phone, call the matching tool
instead of describing the steps. Never say an action is done unless a tool result
confirms it. If no tool fits, just answer conversationally.

Remember the user's stated preferences and use them."""
    }
}
