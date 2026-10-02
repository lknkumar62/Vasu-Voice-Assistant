package com.vasu.assistant

import com.vasu.assistant.core.ai.ToolCallLoopPlanner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the multi-step tool-call loop policy: continue while rounds remain,
 * stop on text, on error, on cancellation, and at the round cap — with the
 * documented precedence (cancel > error > text > cap).
 */
class ToolCallLoopPlannerTest {

    @Test
    fun `continues while tool rounds remain`() {
        val decision = ToolCallLoopPlanner.decide(completedRounds = 1, maxRounds = 3)
        assertEquals(ToolCallLoopPlanner.Decision.CONTINUE, decision)
    }

    @Test
    fun `stops when max rounds reached`() {
        val decision = ToolCallLoopPlanner.decide(completedRounds = 3, maxRounds = 3)
        assertEquals(ToolCallLoopPlanner.Decision.STOP_MAX_ROUNDS, decision)
    }

    @Test
    fun `stops when max rounds exceeded`() {
        val decision = ToolCallLoopPlanner.decide(completedRounds = 4, maxRounds = 3)
        assertEquals(ToolCallLoopPlanner.Decision.STOP_MAX_ROUNDS, decision)
    }

    @Test
    fun `default cap allows exactly three rounds`() {
        assertEquals(
            ToolCallLoopPlanner.Decision.CONTINUE,
            ToolCallLoopPlanner.decide(completedRounds = ToolCallLoopPlanner.MAX_TOOL_ROUNDS - 1)
        )
        assertEquals(
            ToolCallLoopPlanner.Decision.STOP_MAX_ROUNDS,
            ToolCallLoopPlanner.decide(completedRounds = ToolCallLoopPlanner.MAX_TOOL_ROUNDS)
        )
    }

    @Test
    fun `stops on text answer even with rounds remaining`() {
        val decision = ToolCallLoopPlanner.decide(completedRounds = 1, maxRounds = 3, hasText = true)
        assertEquals(ToolCallLoopPlanner.Decision.STOP_TEXT, decision)
    }

    @Test
    fun `stops on provider error even with rounds remaining`() {
        val decision = ToolCallLoopPlanner.decide(completedRounds = 1, maxRounds = 3, lastError = true)
        assertEquals(ToolCallLoopPlanner.Decision.STOP_ERROR, decision)
    }

    @Test
    fun `error beats text in precedence`() {
        val decision = ToolCallLoopPlanner.decide(
            completedRounds = 1,
            maxRounds = 3,
            hasText = true,
            lastError = true
        )
        assertEquals(ToolCallLoopPlanner.Decision.STOP_ERROR, decision)
    }

    @Test
    fun `cancellation is a safe stop and beats everything`() {
        val cancelled = ToolCallLoopPlanner.decide(completedRounds = 0, maxRounds = 3, cancelled = true)
        assertEquals(ToolCallLoopPlanner.Decision.STOP_CANCELLED, cancelled)

        val cancelledWithTextAndError = ToolCallLoopPlanner.decide(
            completedRounds = 3,
            maxRounds = 3,
            hasText = true,
            lastError = true,
            cancelled = true
        )
        assertEquals(ToolCallLoopPlanner.Decision.STOP_CANCELLED, cancelledWithTextAndError)
    }

    @Test
    fun `text beats round cap so a final answer is never discarded`() {
        val decision = ToolCallLoopPlanner.decide(completedRounds = 3, maxRounds = 3, hasText = true)
        assertEquals(ToolCallLoopPlanner.Decision.STOP_TEXT, decision)
    }
}
