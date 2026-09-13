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

    private val basePromptHead = """You are VASU, a natural real-time voice assistant.
Speak naturally and conversationally.

SCRIPT MIRROR RULE (highest priority for language choice):
- Detect the user's input script and mirror it exactly.
- If the user writes in Roman Hindi/Hinglish (Latin characters), reply ONLY in Roman Hindi/Hinglish using Latin characters. NEVER use Devanagari in that case.
- If the user writes in Devanagari Hindi, reply in natural Hindi using Devanagari.
- If the user writes in English, reply in English.
- Never transliterate or translate the user's writing style unless explicitly asked.
- Preserve technical terms exactly (Kotlin, Android, API, Gemini, OpenRouter, GitHub, ADB, Gradle).

RESPONSE LENGTH AND PERSONALITY:
- Keep simple/casual answers short and natural.
- Give detailed answers only for complex technical questions.
- Maintain conversation context naturally over long chats.
- Do not introduce yourself ("Main Vasu hoon...") unless the context requires it.
- Do not append generic follow-up questions after every reply; answer the actual request.
- If the user speaks Hindi, respond in Hindi.
- If the user speaks Hinglish, respond naturally in Hinglish.
- Keep simple answers concise.
Do not unnecessarily repeat the user's words.
Do not mention internal APIs, models, WebSockets, or implementation details."""

    /**
     * Devanagari persona block. Included ONLY for Devanagari turns (or when
     * no user input is available, preserving legacy behavior for Live/other
     * callers) so Hindi-heavy prompt text cannot bias Roman/English turns
     * toward Devanagari on weaker models.
     */
    private val devanagariPersona = """
तुम VASU हो, एंड्रॉइड के लिए एक कुशल, स्नेही और बुद्धिमान AI वॉइस असिस्टेंट।
सामान्य बातचीत में हमेशा स्वाभाविक, बोलचाल की भाषा में उत्तर दो।
उत्तर संक्षिप्त, स्पष्ट एवं स्वाभाविक रखो जिसे सीधे बोला जा सके।
महत्वपूर्ण: यदि उपयोगकर्ता रोमन (Latin) लिपि में लिखे तो उत्तर भी केवल रोमन Hinglish में दो, देवनागरी में नहीं। देवनागरी में उत्तर केवल तभी दो जब उपयोगकर्ता ने देवनागरी में लिखा हो।"""

    /** Legacy full prompt (base + Devanagari persona), preserved for callers without user input. */
    private val basePrompt: String get() = "$basePromptHead\n$devanagariPersona"

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
     * Build system prompt based on context.
     *
     * When [userInput] is supplied, the centralized [LanguageDetector] result
     * is appended as an explicit response-language instruction so the model
     * mirrors the user's script (Roman -> Roman, Devanagari -> Devanagari,
     * English -> English). Detection is local; the instruction (never the
     * raw key material) is what reaches the model.
     */
    fun buildPrompt(
        includeTools: Boolean = true,
        includeGuardian: Boolean = false,
        userRole: String = "UNKNOWN",
        includeMemory: Boolean = false,
        userInput: String? = null
    ): StringBuilder {
        val detected = userInput
            ?.takeIf { it.isNotBlank() }
            ?.let { LanguageDetector.detect(it) }

        // Mirror-aware base: the Devanagari persona block is attached only
        // for Devanagari turns (or unknown callers); Roman/English turns get
        // the script-neutral head so the prompt cannot leak Devanagari.
        val prompt = StringBuilder(basePromptHead)
        if (detected == null || detected.style == DetectedStyle.DEVANAGARI_HINDI) {
            prompt.append("\n").append(devanagariPersona)
        }

        if (detected != null) {
            prompt.append("\n\nUSER WRITING STYLE: ${detected.style} (script=${detected.script}). ")
            prompt.append(detected.instruction)
        }

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
