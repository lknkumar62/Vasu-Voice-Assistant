package com.vasu.assistant.core.ai

import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.tts.TTSManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIOrchestrator - Main coordinator for AI processing.
 *
 * Flow:
 * 1. Receive user input (text or voice)
 * 2. Parse intent
 * 3. Route to appropriate tool
 * 4. Execute tool
 * 5. Generate response
 * 6. Return to user (text + TTS)
 */
@Singleton
class AIOrchestrator @Inject constructor(
    private val aiClient: AIClient,
    private val intentParser: IntentParser,
    private val toolRouter: ToolRouter,
    private val ttsManager: TTSManager
) {
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse.asStateFlow()

    /**
     * Process user input
     */
    suspend fun processInput(input: String): String {
        _isProcessing.value = true

        return try {
            // Step 1: Parse intent
            val intent = intentParser.parse(input)

            // Step 2: Route to tool if applicable
            val toolResult = if (intent.intent != IntentType.CHAT && intent.intent != IntentType.UNKNOWN) {
                executeToolForIntent(intent)
            } else {
                null
            }

            // Step 3: Generate response
            val response = if (toolResult != null) {
                if (toolResult.success) {
                    "Done! ${toolResult.message}"
                } else {
                    "I couldn't complete that: ${toolResult.message}"
                }
            } else {
                // Use AI for chat
                val aiResponse = aiClient.chat(
                    AIRequest(
                        prompt = input,
                        systemPrompt = getSystemPrompt()
                    )
                )
                aiResponse.content
            }

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

    /**
     * Process voice input
     */
    suspend fun processVoiceInput(transcript: String): String {
        return processInput(transcript)
    }

    /**
     * Speak response
     */
    fun speakResponse(text: String) {
        ttsManager.speakQueued(text)
    }

    /**
     * Stop processing
     */
    fun stop() {
        _isProcessing.value = false
        ttsManager.stop()
    }

    private suspend fun executeToolForIntent(intent: ParsedIntent): ActionResult {
        return when (intent.intent) {
            IntentType.OPEN_APP -> {
                val pkg = intent.entities["package"] ?: ""
                toolRouter.executeTool("open_app", mapOf("package" to pkg))
            }
            IntentType.CLICK -> {
                val text = intent.entities["text"] ?: ""
                toolRouter.executeTool("click", mapOf("text" to text))
            }
            IntentType.TYPE_TEXT -> {
                val text = intent.entities["text"] ?: ""
                toolRouter.executeTool("type_text", mapOf("text" to text, "label" to ""))
            }
            IntentType.SCROLL -> {
                val direction = if (intent.rawText.contains("upar") || intent.rawText.contains("up")) "up" else "down"
                toolRouter.executeTool(if (direction == "up") "scroll_up" else "scroll_down", emptyMap())
            }
            IntentType.READ_SCREEN -> {
                toolRouter.executeTool("read_screen", emptyMap())
            }
            IntentType.SEND_MESSAGE -> {
                val message = intent.entities["message"] ?: ""
                toolRouter.executeTool("send_message", mapOf("contact" to "Unknown", "message" to message))
            }
            IntentType.MAKE_CALL -> {
                val contact = intent.entities["contact"] ?: ""
                toolRouter.executeTool("make_call", mapOf("number" to contact))
            }
            IntentType.SET_VOLUME -> {
                val level = intent.entities["level"]?.toIntOrNull() ?: 50
                toolRouter.executeTool("set_volume", mapOf("level" to level))
            }
            IntentType.TOGGLE_TORCH -> {
                val enabled = !intent.rawText.contains("off")
                toolRouter.executeTool("turn_on_torch", mapOf("enabled" to enabled))
            }
            IntentType.CREATE_ALARM -> {
                val time = intent.entities["time"] ?: "08:00"
                toolRouter.executeTool("create_alarm", mapOf("time" to time, "label" to "Alarm"))
            }
            IntentType.SEARCH_WEB -> {
                val query = intent.entities["query"] ?: ""
                toolRouter.executeTool("search_web", mapOf("query" to query))
            }
            IntentType.GO_BACK -> {
                toolRouter.executeTool("press_back", emptyMap())
            }
            IntentType.GO_HOME -> {
                toolRouter.executeTool("press_home", emptyMap())
            }
            else -> ActionResult.success("chat", "Chat response")
        }
    }

    private fun getSystemPrompt(): String {
        return """You are VASU, a helpful AI voice assistant for Android.
You speak in Hindi, English, or Hinglish based on what the user uses.
Be helpful, concise, and friendly.
You can control the phone, send messages, make calls, and more."""
    }
}
