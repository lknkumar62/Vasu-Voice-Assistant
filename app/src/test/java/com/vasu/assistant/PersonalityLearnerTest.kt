package com.vasu.assistant

import com.vasu.assistant.core.personality.InteractionSignal
import com.vasu.assistant.core.personality.PersonalityLearner
import com.vasu.assistant.core.personality.PersonalityModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the learner contract: keyword signals, clamping, and the rule that
 * only feedback signals land in learnedKeys (emoji mimicry never does).
 */
class PersonalityLearnerTest {

    @Test
    fun `brief request lowers verbosity`() {
        val signals = PersonalityLearner.extractSignals("Keep it brief please")
        assertTrue(signals.any { it.preference == "verbosity" && it.direction == -1 })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals(4, updated.learned.verbosity)
        assertTrue("verbosity" in updated.learnedKeys)
    }

    @Test
    fun `explain request raises verbosity`() {
        val signals = PersonalityLearner.extractSignals("Please explain this")
        assertTrue(signals.any { it.preference == "verbosity" && it.direction == 1 })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals(6, updated.learned.verbosity)
    }

    @Test
    fun `casual request lowers formality`() {
        val signals = PersonalityLearner.extractSignals("Be more casual with me")
        assertTrue(signals.any { it.preference == "formality" && it.direction == -1 })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals(4, updated.learned.formality)
        assertTrue("formality" in updated.learnedKeys)
    }

    @Test
    fun `professional request raises formality`() {
        val signals = PersonalityLearner.extractSignals("Keep it professional")
        assertTrue(signals.any { it.preference == "formality" && it.direction == 1 })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals(6, updated.learned.formality)
    }

    @Test
    fun `joke request raises humor`() {
        val signals = PersonalityLearner.extractSignals("Tell me a joke")
        assertTrue(signals.any { it.preference == "humorLevel" && it.direction == 1 })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals(4, updated.learned.humorLevel)
        assertTrue("humorLevel" in updated.learnedKeys)
    }

    @Test
    fun `serious request lowers humor`() {
        val signals = PersonalityLearner.extractSignals("This is a serious matter")
        assertTrue(signals.any { it.preference == "humorLevel" && it.direction == -1 })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals(2, updated.learned.humorLevel)
    }

    @Test
    fun `emoji message sets emoji usage without learned key`() {
        val signals = PersonalityLearner.extractSignals("Thanks, that helped \uD83D\uDE0A")
        assertTrue(signals.any { it.preference == "emojiUsage" && it.boolValue == true })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertTrue(updated.learned.emojiUsage)
        assertTrue("emojiUsage" !in updated.learnedKeys)
    }

    @Test
    fun `plain message without emoji yields no emoji signal`() {
        val signals = PersonalityLearner.extractSignals("What is the time?")
        assertTrue(signals.none { it.preference == "emojiUsage" })
    }

    @Test
    fun `list request sets lists format`() {
        val signals = PersonalityLearner.extractSignals("Show me as bullet points")
        assertTrue(signals.any { it.preference == "preferredFormat" && it.value == "lists" })
        val updated = PersonalityLearner.applySignals(PersonalityModel(), signals)
        assertEquals("lists", updated.learned.preferredFormat)
        assertTrue("preferredFormat" in updated.learnedKeys)
    }

    @Test
    fun `table request sets tables format`() {
        val signals = PersonalityLearner.extractSignals("Show me this as a table")
        assertTrue(signals.any { it.preference == "preferredFormat" && it.value == "tables" })
    }

    @Test
    fun `paragraph request sets prose format`() {
        val signals = PersonalityLearner.extractSignals("Write it out in prose")
        assertTrue(signals.any { it.preference == "preferredFormat" && it.value == "prose" })
    }

    @Test
    fun `numeric preferences clamp at bounds`() {
        val down = InteractionSignal("user_feedback", "verbosity", direction = -1)
        val up = InteractionSignal("user_feedback", "verbosity", direction = 1)
        var model = PersonalityModel()
        repeat(20) { model = PersonalityLearner.applySignals(model, listOf(down)) }
        assertEquals(0, model.learned.verbosity)
        repeat(30) { model = PersonalityLearner.applySignals(model, listOf(up)) }
        assertEquals(10, model.learned.verbosity)
    }

    @Test
    fun `recordInteraction grows count and trust`() {
        var model = PersonalityModel()
        model = PersonalityLearner.recordInteraction(model)
        assertEquals(1, model.messageCount)
        assertEquals(3, model.trustLevel)
        repeat(9) { model = PersonalityLearner.recordInteraction(model) }
        assertEquals(10, model.messageCount)
        assertEquals(4, model.trustLevel)
    }
}
