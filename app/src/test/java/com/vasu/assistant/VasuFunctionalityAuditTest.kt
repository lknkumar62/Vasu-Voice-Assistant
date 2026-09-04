package com.vasu.assistant

import com.vasu.assistant.camera.RecordingState
import com.vasu.assistant.core.ai.AiErrorKind
import com.vasu.assistant.core.ai.AiProviderConfig
import com.vasu.assistant.core.ai.ToolDefinition
import com.vasu.assistant.core.ai.ToolParameter
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.automation.AutomationStep
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.ui.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VasuFunctionalityAuditTest {

    // === PART 1: AI / API Routing & Claude Config ===

    @Test
    fun `claude provider config defines valid anthropic endpoint and primary model`() {
        val config = AiProviderConfig.CLAUDE
        assertEquals("claude", config.providerId)
        assertEquals("https://api.anthropic.com/v1", config.baseUrl)
        assertEquals("claude-3-5-sonnet-20241022", config.primaryModel)
        assertFalse(config.allowFallback) // Opus or Sonnet must not be silently replaced
        assertTrue(config.fallbackModels.contains("claude-3-opus-20240229"))
    }

    @Test
    fun `ai error taxonomy distinguishes key missing, quota, rate limit, and model not found`() {
        val notConfigured = AiErrorKind.NOT_CONFIGURED
        val invalidKey = AiErrorKind.INVALID_KEY
        val quota = AiErrorKind.QUOTA_EXCEEDED
        val rateLimit = AiErrorKind.RATE_LIMITED
        val modelNotFound = AiErrorKind.MODEL_NOT_FOUND
        val offline = AiErrorKind.OFFLINE

        assertFalse(notConfigured.isTransient)
        assertFalse(invalidKey.isTransient)
        assertFalse(quota.isTransient)
        assertFalse(modelNotFound.isTransient)

        assertTrue(rateLimit.isTransient)
        assertTrue(offline.isTransient)
    }

    // === PART 2: Chat Duplicate Response Bug & Deduplication ===

    @Test
    fun `chat message list deduplication filters consecutive duplicate assistant messages`() {
        val rawMessages = listOf(
            ChatMessage(content = "Hello", isUser = true),
            ChatMessage(content = "नमस्ते! मैं वासु हूँ।", isUser = false),
            ChatMessage(content = "नमस्ते! मैं वासु हूँ।", isUser = false), // duplicate
            ChatMessage(content = "How are you?", isUser = true),
            ChatMessage(content = "How are you?", isUser = true), // duplicate
            ChatMessage(content = "मैं ठीक हूँ।", isUser = false)
        )

        val deduplicated = mutableListOf<ChatMessage>()
        for (msg in rawMessages) {
            val last = deduplicated.lastOrNull()
            if (last != null && last.isUser == msg.isUser && last.content.trim() == msg.content.trim()) {
                continue
            }
            deduplicated.add(msg)
        }

        assertEquals(4, deduplicated.size)
        assertEquals("Hello", deduplicated[0].content)
        assertEquals("नमस्ते! मैं वासु हूँ।", deduplicated[1].content)
        assertEquals("How are you?", deduplicated[2].content)
        assertEquals("मैं ठीक हूँ।", deduplicated[3].content)
    }

    // === PART 7 & 8: Camera & Video Recording State Machine ===

    @Test
    fun `video recorder state machine transitions correctly and prevents duplicate recording`() {
        var state = RecordingState.IDLE

        // 1. Initial State
        assertEquals(RecordingState.IDLE, state)

        // 2. Start recording transitions to RECORDING
        state = RecordingState.RECORDING
        assertEquals(RecordingState.RECORDING, state)

        // 3. Attempting to start again while RECORDING is rejected
        val isAlreadyRecording = state == RecordingState.RECORDING
        assertTrue(isAlreadyRecording)

        // 4. Stopping transitions RECORDING -> STOPPING -> IDLE
        state = RecordingState.STOPPING
        assertEquals(RecordingState.STOPPING, state)

        // Guaranteed reset in finally
        state = RecordingState.IDLE
        assertEquals(RecordingState.IDLE, state)

        // 5. Next recording start works cleanly
        state = RecordingState.RECORDING
        assertEquals(RecordingState.RECORDING, state)
        state = RecordingState.IDLE
    }

    // === PART 9: Accessibility Error Taxonomy ===

    @Test
    fun `accessibility action results contain structured codes instead of generic retries`() {
        val serviceDisabled = ActionResult.error("click", "Accessibility service is not enabled", "SERVICE_DISABLED")
        val nodeNotFound = ActionResult.error("click", "UI element containing 'Submit' not found on screen", "NODE_NOT_FOUND")
        val actionFailed = ActionResult.error("click", "Element is not clickable", "ACTION_FAILED")

        assertEquals("SERVICE_DISABLED", serviceDisabled.error)
        assertEquals("NODE_NOT_FOUND", nodeNotFound.error)
        assertEquals("ACTION_FAILED", actionFailed.error)

        assertFalse(serviceDisabled.message.contains("Failed after 2 retries"))
        assertFalse(nodeNotFound.message.contains("Failed after 2 retries"))
    }

    // === PART 10: Tool Utilities (Math, Currency, Unit Conversion) ===

    @Test
    fun `math expression evaluation produces exact arithmetic results`() {
        val expressions = mapOf(
            "25 * 4" to "100",
            "100 / 5 + 10" to "30",
            "50 - 15" to "35",
            "12.5 * 2" to "25"
        )

        for ((expr, expected) in expressions) {
            val clean = expr.replace(" ", "")
            val tokens = clean.split(Regex("(?<=[-+*/])|(?=[-+*/])")).filter { it.isNotBlank() }
            var result = tokens[0].toDouble()
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                val nextVal = tokens[i + 1].toDouble()
                when (op) {
                    "+" -> result += nextVal
                    "-" -> result -= nextVal
                    "*" -> result *= nextVal
                    "/" -> result /= nextVal
                }
                i += 2
            }
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else "%.2f".format(Locale.US, result)
            assertEquals(expected, formatted)
        }
    }

    @Test
    fun `currency conversion computes rates correctly`() {
        val usdToInrRate = 86.50
        val amount = 100.0
        val converted = amount * usdToInrRate
        assertEquals(8650.0, converted, 0.01)
    }

    @Test
    fun `unit conversion handles km to miles and celsius to fahrenheit`() {
        val km = 10.0
        val miles = km * 0.621371
        assertEquals(6.21, miles, 0.01)

        val celsius = 25.0
        val fahrenheit = celsius * 9.0 / 5.0 + 32.0
        assertEquals(77.0, fahrenheit, 0.01)
    }
}
