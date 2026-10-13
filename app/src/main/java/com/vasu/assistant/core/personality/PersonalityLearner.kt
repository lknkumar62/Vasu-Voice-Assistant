package com.vasu.assistant.core.personality

/**
 * PersonalityLearner - Extracts preference signals from user messages and
 * applies them to the personality model.
 *
 * Ported from the Jarvis personality engine (src/personality/learner.ts).
 * Pure Kotlin with no Android dependencies so the learner stays usable
 * from JVM unit tests.
 *
 * Learning is gradual: every numeric signal moves its preference by exactly
 * one step, clamped to 0..10.
 */
object PersonalityLearner {

    private const val TYPE_FEEDBACK = "user_feedback"
    private const val TYPE_STYLE = "message_style"

    /**
     * Analyze a user message for preference signals.
     *
     * Matching is case-insensitive keyword containment on the lowercased
     * message. Emoji usage is detected by scanning code points in the
     * common symbol/pictograph ranges.
     */
    fun extractSignals(userMsg: String): List<InteractionSignal> {
        val signals = mutableListOf<InteractionSignal>()
        val lower = userMsg.lowercase()

        if (lower.contains("shorter") || lower.contains("brief") ||
            lower.contains("tldr") || lower.contains("too long") ||
            lower.contains("concise") || lower.contains("summarize")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "verbosity", direction = -1))
        }

        if (lower.contains("more detail") || lower.contains("explain") ||
            lower.contains("elaborate") || lower.contains("tell me more") ||
            lower.contains("expand on")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "verbosity", direction = 1))
        }

        if (lower.contains("be more casual") || lower.contains("less formal") ||
            lower.contains("relax") || lower.contains("informal")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "formality", direction = -1))
        }

        if (lower.contains("be more formal") || lower.contains("professional") ||
            lower.contains("polite")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "formality", direction = 1))
        }

        if (lower.contains("funny") || lower.contains("joke") ||
            lower.contains("humorous") || lower.contains("make me laugh")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "humorLevel", direction = 1))
        }

        if (lower.contains("serious") || lower.contains("no jokes") ||
            lower.contains("be serious")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "humorLevel", direction = -1))
        }

        if (containsEmoji(userMsg)) {
            signals.add(InteractionSignal(TYPE_STYLE, "emojiUsage", boolValue = true))
        }

        if (lower.contains("bullet points") || lower.contains("list format") ||
            lower.contains("as a list")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "preferredFormat", value = "lists"))
        }

        if (lower.contains("table") || lower.contains("tabular format")) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "preferredFormat", value = "tables"))
        }

        if (lower.contains("paragraph") || lower.contains("prose") ||
            lower.contains("written out")
        ) {
            signals.add(InteractionSignal(TYPE_FEEDBACK, "preferredFormat", value = "prose"))
        }

        return signals
    }

    /**
     * Apply signals to a personality model.
     *
     * Numeric preferences move by [InteractionSignal.direction], clamped to
     * 0..10. Only non-message_style signals are recorded in [learnedKeys],
     * so passive mimicry (emoji usage) never outranks a channel preset.
     */
    fun applySignals(model: PersonalityModel, signals: List<InteractionSignal>): PersonalityModel {
        var learned = model.learned
        val keys = model.learnedKeys.toMutableSet()

        for (signal in signals) {
            val isExplicit = signal.type != TYPE_STYLE
            when (signal.preference) {
                "verbosity" ->
                    learned = learned.copy(verbosity = clamp(learned.verbosity + signal.direction))
                "formality" ->
                    learned = learned.copy(formality = clamp(learned.formality + signal.direction))
                "humorLevel" ->
                    learned = learned.copy(humorLevel = clamp(learned.humorLevel + signal.direction))
                "emojiUsage" ->
                    if (signal.boolValue != null) learned = learned.copy(emojiUsage = signal.boolValue)
                    else continue
                "preferredFormat" ->
                    if (signal.value in setOf("lists", "prose", "tables", "adaptive")) {
                        learned = learned.copy(preferredFormat = signal.value!!)
                    } else {
                        continue
                    }
                else -> continue
            }
            if (isExplicit) keys.add(signal.preference)
        }

        return model.copy(learned = learned, learnedKeys = keys)
    }

    /**
     * Record one interaction: increments the message count and recomputes
     * trust as min(10, 3 + count / 10).
     */
    fun recordInteraction(model: PersonalityModel): PersonalityModel {
        val count = model.messageCount + 1
        val trust = minOf(10, 3 + count / 10)
        return model.copy(messageCount = count, trustLevel = trust)
    }

    private fun clamp(value: Int, min: Int = 0, max: Int = 10): Int =
        value.coerceIn(min, max)

    /**
     * True when any code point falls in the misc-symbol, dingbat, or
     * supplementary pictograph ranges.
     */
    private fun containsEmoji(message: String): Boolean {
        var index = 0
        while (index < message.length) {
            val codePoint = message.codePointAt(index)
            if (codePoint in 0x2600..0x27BF ||
                codePoint in 0x2B00..0x2BFF ||
                codePoint in 0x1F000..0x1FAFF
            ) {
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }
}
