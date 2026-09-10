package com.vasu.assistant.core.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * PromptManager - Manages system prompts for AI interactions.
 *
 * Maintains context-aware prompts based on:
 * - User role (from Voice Guardian)
 * - Current screen state
 * - Conversation history
 * - Available tools
 */
@Singleton
class PromptManager @Inject constructor() {

    private val basePrompt = """You are VASU, a natural real-time voice assistant.
Speak naturally and conversationally.
Respond in the user's language.
If the user speaks Hindi, respond in Hindi.
If the user speaks Hinglish, respond naturally in Hinglish.
Keep simple answers concise.
Do not unnecessarily repeat the user's words.
Do not mention internal APIs, models, WebSockets, or implementation details.

तुम VASU हो, एंड्रॉइड के लिए एक कुशल, स्नेही और बुद्धिमान AI वॉइस असिस्टेंट।
सामान्य बातचीत में हमेशा स्वाभाविक, बोलचाल की भाषा में उत्तर दो।
उत्तर संक्षिप्त, स्पष्ट एवं स्वाभाविक रखो जिसे सीधे बोला जा सके।"""

    private val toolPrompt = """
You have access to tools to control the phone:
- Open/close apps
- Click buttons and navigate
- Type text
- Read screen content
- Send messages and make calls
- Control volume, torch, alarms
- Search the web

When the user asks you to do something, use the appropriate tool.
Always confirm what you did after performing an action."""

    private val guardianPrompt = """
SECURITY NOTICE: Voice Guardian is active.
Current speaker role: {ROLE}
Only allow actions appropriate for this role.
- BOSS: Full access
- FAMILY: Normal assistant functions
- FRIEND: Informational only
- GUEST: Conversation only
- BLOCKED: Deny all commands"""

    private val memoryPrompt = """
You have access to conversation history and user preferences.
Use this context to provide personalized responses.
Remember user's name, preferences, and past interactions."""

    /**
     * Build system prompt based on context
     */
    fun buildPrompt(
        includeTools: Boolean = true,
        includeGuardian: Boolean = false,
        userRole: String = "UNKNOWN",
        includeMemory: Boolean = false
    ): StringBuilder {
        val prompt = StringBuilder(basePrompt)

        if (includeTools) {
            prompt.append("\n").append(toolPrompt)
        }

        if (includeGuardian) {
            prompt.append("\n").append(guardianPrompt.replace("{ROLE}", userRole))
        }

        if (includeMemory) {
            prompt.append("\n").append(memoryPrompt)
        }

        return prompt
    }

    /**
     * Build context prompt for current screen
     */
    fun buildScreenContext(screenContent: String): String {
        return "\nCurrent screen shows: $screenContent"
    }

    /**
     * Build conversation context
     */
    fun buildConversationContext(history: List<ChatMessage>): String {
        if (history.isEmpty()) return ""

        val recentMessages = history.takeLast(10)
        return "\nRecent conversation:\n" + recentMessages.joinToString("\n") {
            "${it.role}: ${it.content}"
        }
    }
}
