package com.vasu.assistant.ui.chat

/**
 * Pure helpers for chat session titles and plain-text export (FIX-5).
 * Kept free of Android dependencies so they are JVM-unit-testable.
 */
object ChatTextFormatter {

    const val MAX_TITLE_LENGTH = 40

    /** First user message, whitespace-collapsed, into a session title. */
    fun deriveTitle(firstUserMessage: String): String {
        val collapsed = firstUserMessage.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return "New chat"
        return if (collapsed.length <= MAX_TITLE_LENGTH) collapsed
        else collapsed.take(MAX_TITLE_LENGTH).trimEnd() + "…"
    }

    /** Plain-text export of a session (share sheet), oldest message first. */
    fun toPlainText(messages: List<ChatMessage>): String {
        val body = messages
            .filter { it.content.isNotBlank() }
            .joinToString("\n\n") { m ->
                val who = when {
                    m.isUser -> "You"
                    m.toolName != null -> "VASU (tool: ${m.toolName})"
                    else -> "VASU"
                }
                "[$who] ${m.content}"
            }
        return if (body.isEmpty()) "VASU chat (empty)" else "VASU chat\n\n$body"
    }
}
