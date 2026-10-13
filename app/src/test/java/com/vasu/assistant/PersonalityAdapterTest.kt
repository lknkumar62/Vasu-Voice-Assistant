package com.vasu.assistant

import com.vasu.assistant.core.personality.LearnedPreferences
import com.vasu.assistant.core.personality.PersonalityAdapter
import com.vasu.assistant.core.personality.PersonalityLearner
import com.vasu.assistant.core.personality.PersonalityModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the adapter contract: channel precedence (stored override first,
 * then learned keys, then preset) and the rendered prompt block.
 */
class PersonalityAdapterTest {

    @Test
    fun `voice preset applies to untouched model`() {
        val prefs = PersonalityAdapter.forChannel(PersonalityModel(), "voice")
        assertEquals(LearnedPreferences(4, 3, 3, false, "lists"), prefs)
    }

    @Test
    fun `email preset is formal prose without emoji`() {
        val prefs = PersonalityAdapter.forChannel(PersonalityModel(), "email")
        assertEquals(7, prefs.verbosity)
        assertEquals(8, prefs.formality)
        assertEquals(false, prefs.emojiUsage)
        assertEquals("prose", prefs.preferredFormat)
    }

    @Test
    fun `learned key outranks channel preset`() {
        var model = PersonalityModel()
        model = PersonalityLearner.applySignals(
            model, PersonalityLearner.extractSignals("Keep it brief please")
        )
        val prefs = PersonalityAdapter.forChannel(model, "email")
        assertEquals(4, prefs.verbosity)
        assertEquals(8, prefs.formality)
    }

    @Test
    fun `stored channel override outranks learned value`() {
        var model = PersonalityModel()
        model = PersonalityLearner.applySignals(
            model, PersonalityLearner.extractSignals("Keep it brief please")
        )
        val withOverride = model.copy(
            channelOverrides = mapOf("email" to LearnedPreferences(9, 9, 1, false, "tables"))
        )
        val prefs = PersonalityAdapter.forChannel(withOverride, "email")
        assertEquals(9, prefs.verbosity)
        assertEquals("tables", prefs.preferredFormat)
    }

    @Test
    fun `unknown channel falls back to learned preferences`() {
        val prefs = PersonalityAdapter.forChannel(PersonalityModel(), "carrier-pigeon")
        assertEquals(PersonalityModel().learned, prefs)
    }

    @Test
    fun `prompt contains traits count and trust`() {
        val model = PersonalityModel(messageCount = 25, trustLevel = 5)
        val prompt = PersonalityAdapter.personalityToPrompt(model)
        assertTrue(prompt.startsWith("## Personality"))
        assertTrue(prompt.contains("direct"))
        assertTrue(prompt.contains("strategic"))
        assertTrue(prompt.contains("resourceful"))
        assertTrue(prompt.contains("25 interactions"))
        assertTrue(prompt.contains("moderate trust (5/10)"))
    }

    @Test
    fun `prompt shows emoji line only when enabled`() {
        val plain = PersonalityAdapter.personalityToPrompt(PersonalityModel())
        assertTrue(!plain.contains("Emoji usage"))
        val withEmoji = PersonalityModel().copy(
            channelOverrides = mapOf("voice" to LearnedPreferences(emojiUsage = true))
        )
        val prompt = PersonalityAdapter.personalityToPrompt(withEmoji)
        assertTrue(prompt.contains("Emoji usage: Enabled"))
    }
}
