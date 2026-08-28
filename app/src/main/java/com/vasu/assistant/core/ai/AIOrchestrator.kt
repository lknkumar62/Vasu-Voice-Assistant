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
            // Store user message
            memoryManager.addMessage("user", input)

            // Process memory commands
            memoryManager.processInput(input)

            // Parse intent
            val intent = intentParser.parse(input)

            // Execute tool if applicable
            val toolResult = if (intent.intent != IntentType.CHAT && intent.intent != IntentType.UNKNOWN) {
                executeToolForIntent(intent)
            } else null

            // Generate response
            val response = if (toolResult != null) {
                if (toolResult.success) "Done! ${toolResult.message}"
                else "I couldn't complete that: ${toolResult.message}"
            } else {
                val context = memoryManager.getFullContext()
                val aiResponse = aiClient.chat(
                    AIRequest(
                        prompt = input,
                        systemPrompt = getSystemPrompt() + if (context.isNotBlank()) "\n\n$context" else ""
                    )
                )
                aiResponse.content
            }

            // Store assistant response
            memoryManager.addMessage("assistant", response)

            _lastResponse.value = response
            _isProcessing.value = false
            response
        } catch (e: Exception) {
            val errorResponse = "Sorry, I encountered an error: ${e.message}"
            _lastResponse.value = errorResponse
            _isProcessing.value = false
            errorResponse
        }
    }

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
        return """You are VASU, a helpful AI voice assistant for Android.
You speak in Hindi, English, or Hinglish based on what the user uses.
Be helpful, concise, and friendly.
You can control the phone, send messages, make calls, and more.
Remember user preferences and personalize responses."""
    }
}
