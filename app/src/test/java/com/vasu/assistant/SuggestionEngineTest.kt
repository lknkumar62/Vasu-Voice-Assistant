package com.vasu.assistant

import com.vasu.assistant.core.awareness.ACTIVITY_OBSERVER
import com.vasu.assistant.core.awareness.AwarenessRole
import com.vasu.assistant.core.awareness.MIN_MATCHED
import com.vasu.assistant.core.awareness.MATCH_THRESHOLD
import com.vasu.assistant.core.awareness.SuggestionEngine
import com.vasu.assistant.core.awareness.isGoalMatch
import com.vasu.assistant.core.awareness.matchScore
import com.vasu.assistant.core.awareness.matchedTerms
import com.vasu.assistant.core.awareness.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private fun sig(kind: SuggestionEngine.Kind, text: String) =
        SuggestionEngine.Signal(kind, text)

    // ---------------------------------------------------------- priority

    @Test
    fun `priority order ERROR beats GENERAL`() {
        val state = SuggestionEngine.State()
        val result = SuggestionEngine.evaluate(
            listOf(sig(SuggestionEngine.Kind.GENERAL, "insight"), sig(SuggestionEngine.Kind.ERROR, "crash")),
            state,
            nowMs = 1_000_000L
        )
        assertEquals(SuggestionEngine.Kind.ERROR, result?.kind)
    }

    @Test
    fun `full priority chain respected`() {
        val state = SuggestionEngine.State()
        val kinds = listOf(
            SuggestionEngine.Kind.BREAK,
            SuggestionEngine.Kind.SCHEDULE,
            SuggestionEngine.Kind.KNOWLEDGE,
            SuggestionEngine.Kind.AUTOMATION,
            SuggestionEngine.Kind.STUCK,
            SuggestionEngine.Kind.STRUGGLE,
            SuggestionEngine.Kind.GENERAL,
            SuggestionEngine.Kind.ERROR
        ).map { sig(it, "text-$it") }
        val result = SuggestionEngine.evaluate(kinds, state, nowMs = 1_000_000L)
        assertEquals(SuggestionEngine.Kind.ERROR, result?.kind)
    }

    @Test
    fun `second priority fires when top rate-limited`() {
        val state = SuggestionEngine.State()
        val now = 1_000_000L
        // Emit ERROR first.
        SuggestionEngine.evaluate(listOf(sig(SuggestionEngine.Kind.ERROR, "crash")), state, now)
        // ERROR again too soon + STRUGGLE eligible -> STRUGGLE wins.
        val result = SuggestionEngine.evaluate(
            listOf(
                sig(SuggestionEngine.Kind.ERROR, "another crash"),
                sig(SuggestionEngine.Kind.STRUGGLE, "retry loop")
            ),
            state,
            nowMs = now + 1_000L
        )
        assertEquals(SuggestionEngine.Kind.STRUGGLE, result?.kind)
    }

    // ------------------------------------------------------- rate limits

    @Test
    fun `rate-limit suppresses repeat of same kind`() {
        val state = SuggestionEngine.State()
        val now = 5_000_000L
        val first = SuggestionEngine.evaluate(
            listOf(sig(SuggestionEngine.Kind.BREAK, "take a break")), state, now
        )
        assertEquals(SuggestionEngine.Kind.BREAK, first?.kind)
        // BREAK limit is 600s; 1s later the same kind must be suppressed.
        val second = SuggestionEngine.evaluate(
            listOf(sig(SuggestionEngine.Kind.BREAK, "take a break please walk")),
            state,
            nowMs = now + 1_000L
        )
        assertNull(second)
    }

    @Test
    fun `rate-limit elapses per kind independently`() {
        val state = SuggestionEngine.State()
        val now = 5_000_000L
        SuggestionEngine.evaluate(listOf(sig(SuggestionEngine.Kind.ERROR, "boom")), state, now)
        // ERROR limit 15s: suppressed at +1s, eligible again at +16s.
        assertNull(
            SuggestionEngine.evaluate(
                listOf(sig(SuggestionEngine.Kind.ERROR, "different boom here")),
                state,
                nowMs = now + 1_000L
            )
        )
        val later = SuggestionEngine.evaluate(
            listOf(sig(SuggestionEngine.Kind.ERROR, "different boom here")),
            state,
            nowMs = now + 16_000L
        )
        assertEquals(SuggestionEngine.Kind.ERROR, later?.kind)
    }

    // ------------------------------------------------------------ dedup

    @Test
    fun `delivered hash not re-emitted after rate limit elapses`() {
        val state = SuggestionEngine.State()
        val now = 9_000_000L
        val signal = sig(SuggestionEngine.Kind.GENERAL, "same insight text")
        SuggestionEngine.evaluate(listOf(signal), state, now)
        // GENERAL limit 60s; after 61s the rate limit passes but the hash blocks.
        val again = SuggestionEngine.evaluate(listOf(signal), state, nowMs = now + 61_000L)
        assertNull(again)
    }

    @Test
    fun `dismissed hash not re-emitted`() {
        val state = SuggestionEngine.State()
        val now = 9_000_000L
        val signal = sig(SuggestionEngine.Kind.KNOWLEDGE, "relevant info entity")
        SuggestionEngine.markDismissed(state, signal)
        val result = SuggestionEngine.evaluate(listOf(signal), state, nowMs = now)
        assertNull(result)
    }

    @Test
    fun `FIFO evicts oldest hash after 50`() {
        val state = SuggestionEngine.State()
        val first = sig(SuggestionEngine.Kind.GENERAL, "first-signal")
        SuggestionEngine.markDismissed(state, first)
        for (i in 1..50) {
            SuggestionEngine.markDismissed(state, sig(SuggestionEngine.Kind.GENERAL, "filler-$i"))
        }
        assertEquals(50, state.hashCount())
        assertFalse(state.contains(SuggestionEngine.hashOf(first)))
    }

    @Test
    fun `empty signals return null`() {
        val state = SuggestionEngine.State()
        assertNull(SuggestionEngine.evaluate(emptyList(), state, nowMs = 1_000L))
    }

    // ----------------------------------------------------- fuzzy matcher

    @Test
    fun `matching pair passes threshold`() {
        val goal = "Morning workout fitness routine exercise"
        val activity = "Morning workout exercise session completed"
        assertTrue(matchScore(goal, activity) >= MATCH_THRESHOLD)
        assertTrue(matchedTerms(goal, activity).size >= MIN_MATCHED)
        assertTrue(isGoalMatch(goal, activity))
    }

    @Test
    fun `unrelated pair fails threshold`() {
        val goal = "Morning workout fitness routine exercise"
        val activity = "Quarterly tax invoice payment receipt"
        assertFalse(isGoalMatch(goal, activity))
    }

    @Test
    fun `stop-words alone never match`() {
        val goal = "Morning workout fitness routine exercise"
        val activity = "the and to of in on for is are"
        assertEquals(0.0, matchScore(goal, activity), 1e-9)
        assertFalse(isGoalMatch(goal, activity))
    }

    // ------------------------------------------------------------ roles

    @Test
    fun `activity observer validates`() {
        assertTrue(validate(ACTIVITY_OBSERVER))
        assertEquals(2, ACTIVITY_OBSERVER.authorityLevel)
        assertTrue(ACTIVITY_OBSERVER.autonomous.isNotEmpty())
        assertTrue(ACTIVITY_OBSERVER.approvalRequired.isNotEmpty())
    }

    @Test
    fun `validate rejects malformed roles`() {
        assertFalse(validate(ACTIVITY_OBSERVER.copy(id = "")))
        assertFalse(validate(ACTIVITY_OBSERVER.copy(name = "  ")))
        assertFalse(validate(ACTIVITY_OBSERVER.copy(authorityLevel = 0)))
        assertFalse(validate(ACTIVITY_OBSERVER.copy(authorityLevel = 11)))
        assertFalse(validate(ACTIVITY_OBSERVER.copy(autonomous = emptyList())))
        assertFalse(validate(ACTIVITY_OBSERVER.copy(approvalRequired = emptyList())))
    }

    @Test
    fun `malformed role baseline`() {
        val bad = AwarenessRole("", "", 99, emptyList(), emptyList(), "")
        assertFalse(validate(bad))
    }
}
