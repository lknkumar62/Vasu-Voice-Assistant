package com.vasu.assistant.core.personality

/**
 * PersonalityModel - Persistent personality state for VASU.
 *
 * Ported from the Jarvis personality engine (src/personality/model.ts).
 * Pure Kotlin with no Android dependencies so the model stays usable
 * from JVM unit tests.
 *
 * The model holds a stable set of [coreTraits], a fully populated set of
 * [learned] preferences, and [learnedKeys] — the only record of which
 * preferences the learner has explicitly moved. A preference that was
 * never learned defers to the channel preset in [PersonalityAdapter].
 */
data class LearnedPreferences(
    val verbosity: Int = 5,
    val formality: Int = 5,
    val humorLevel: Int = 3,
    val emojiUsage: Boolean = false,
    val preferredFormat: String = "adaptive"
)

/**
 * Interaction signal extracted from a user message.
 *
 * [type] is "user_feedback" for keyword-detected preferences and
 * "message_style" for passive style mimicry (emoji usage). Only
 * non-message_style signals are recorded in [PersonalityModel.learnedKeys].
 */
data class InteractionSignal(
    val type: String,
    val preference: String,
    val direction: Int = 0,
    val value: String? = null,
    val boolValue: Boolean? = null
)

data class PersonalityModel(
    val coreTraits: List<String> = listOf("direct", "strategic", "resourceful"),
    val learned: LearnedPreferences = LearnedPreferences(),
    val learnedKeys: Set<String> = emptySet(),
    val messageCount: Int = 0,
    val trustLevel: Int = 0,
    val sharedReferences: List<String> = emptyList(),
    val channelOverrides: Map<String, LearnedPreferences> = emptyMap()
)
