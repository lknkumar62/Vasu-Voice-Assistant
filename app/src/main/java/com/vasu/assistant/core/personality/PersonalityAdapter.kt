package com.vasu.assistant.core.personality

/**
 * PersonalityAdapter - Resolves channel-specific preferences and renders
 * the personality block for LLM system prompts.
 *
 * Ported from the Jarvis personality engine (src/personality/adapter.ts).
 * Pure Kotlin with no Android dependencies so the adapter stays usable
 * from JVM unit tests.
 *
 * Precedence for [forChannel]: a stored per-channel override wins outright,
 * then every preference recorded in [PersonalityModel.learnedKeys] keeps
 * its learned value, and the channel preset fills in the rest.
 */
object PersonalityAdapter {

    /** Channel presets: terse + emoji-friendly for chat, formal for email. */
    val CHANNEL_DEFAULTS: Map<String, LearnedPreferences> = mapOf(
        "whatsapp" to LearnedPreferences(4, 3, 5, true, "lists"),
        "telegram" to LearnedPreferences(4, 3, 5, true, "lists"),
        "discord" to LearnedPreferences(4, 3, 5, true, "lists"),
        "email" to LearnedPreferences(7, 8, 2, false, "prose"),
        "terminal" to LearnedPreferences(5, 5, 3, false, "adaptive"),
        "websocket" to LearnedPreferences(5, 5, 3, false, "adaptive"),
        "voice" to LearnedPreferences(4, 3, 3, false, "lists")
    )

    /**
     * Resolve the effective preferences for [channel].
     *
     * Unknown channels fall back to the model's learned preferences.
     */
    fun forChannel(model: PersonalityModel, channel: String): LearnedPreferences {
        val key = channel.lowercase()
        model.channelOverrides[key]?.let { return it }

        val defaults = CHANNEL_DEFAULTS[key] ?: return model.learned
        val learned = model.learned
        val keys = model.learnedKeys
        return LearnedPreferences(
            verbosity = if ("verbosity" in keys) learned.verbosity else defaults.verbosity,
            formality = if ("formality" in keys) learned.formality else defaults.formality,
            humorLevel = if ("humorLevel" in keys) learned.humorLevel else defaults.humorLevel,
            emojiUsage = if ("emojiUsage" in keys) learned.emojiUsage else defaults.emojiUsage,
            preferredFormat = if ("preferredFormat" in keys) learned.preferredFormat else defaults.preferredFormat
        )
    }

    /**
     * Render the personality block for an LLM system prompt.
     */
    fun personalityToPrompt(model: PersonalityModel, channel: String = "voice"): String {
        val prefs = forChannel(model, channel)
        val lines = mutableListOf("## Personality")

        if (model.coreTraits.isNotEmpty()) {
            lines.add("Core traits: ${model.coreTraits.joinToString(", ")}")
        }

        lines.add(
            "Communication: ${verbosityDescription(prefs.verbosity)} verbosity " +
                "(${prefs.verbosity}/10), ${formalityDescription(prefs.formality)} " +
                "formality (${prefs.formality}/10), ${humorDescription(prefs.humorLevel)} humor"
        )
        lines.add("Format preference: ${prefs.preferredFormat}")
        if (prefs.emojiUsage) {
            lines.add("Emoji usage: Enabled")
        }
        lines.add(
            "Relationship: ${model.messageCount} interactions, " +
                "${trustDescription(model.trustLevel)} trust (${model.trustLevel}/10)"
        )
        if (model.sharedReferences.isNotEmpty()) {
            lines.add("Shared references: ${model.sharedReferences.joinToString(", ")}")
        }

        return lines.joinToString("\n")
    }

    private fun verbosityDescription(level: Int): String = when {
        level <= 2 -> "Very brief"
        level <= 4 -> "Concise"
        level <= 6 -> "Moderate"
        level <= 8 -> "Detailed"
        else -> "Very detailed"
    }

    private fun formalityDescription(level: Int): String = when {
        level <= 2 -> "Very casual"
        level <= 4 -> "Casual"
        level <= 6 -> "Moderate"
        level <= 8 -> "Formal"
        else -> "Very formal"
    }

    private fun humorDescription(level: Int): String = when {
        level <= 2 -> "minimal"
        level <= 4 -> "light"
        level <= 6 -> "moderate"
        level <= 8 -> "frequent"
        else -> "heavy"
    }

    private fun trustDescription(level: Int): String = when {
        level <= 2 -> "low"
        level <= 4 -> "developing"
        level <= 6 -> "moderate"
        level <= 8 -> "high"
        else -> "very high"
    }
}
