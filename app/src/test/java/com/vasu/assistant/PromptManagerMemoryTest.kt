package com.vasu.assistant

import com.vasu.assistant.core.ai.PromptManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the FIX-3 prompt work: recalled memory facts reach the
 * system prompt only when a non-blank block is supplied, and the prompt carries
 * the guard that forbids claiming unconfirmed actions.
 */
class PromptManagerMemoryTest {

    private val promptManager = PromptManager()

    @Test
    fun memoryFactsAreIncludedWhenProvided() {
        val prompt = promptManager.buildPrompt(
            userInput = "kya yaad hai",
            includeMemory = true,
            memoryBlock = "user_name: Rahul"
        ).toString()

        assertTrue("facts block must be present", prompt.contains("user_name: Rahul"))
    }

    @Test
    fun memoryFactsAreIgnoredWhenMemoryDisabled() {
        val prompt = promptManager.buildPrompt(
            userInput = "hello",
            includeMemory = false,
            memoryBlock = "user_name: Rahul"
        ).toString()

        assertFalse("facts must not leak without includeMemory", prompt.contains("user_name: Rahul"))
    }

    @Test
    fun blankMemoryBlockIsHarmless() {
        val prompt = promptManager.buildPrompt(
            userInput = "hello",
            includeMemory = true,
            memoryBlock = "   "
        ).toString()

        assertTrue("prompt still built", prompt.isNotBlank())
    }

    @Test
    fun promptForbidsClaimingUnconfirmedActions() {
        val prompt = promptManager.buildPrompt(userInput = "sms bhej do").toString()

        assertTrue(prompt.contains("Never claim an action was performed"))
    }

    @Test
    fun toolsSectionRestrainsCasualToolCalls() {
        val withTools = promptManager.buildPrompt(includeTools = true).toString()
        assertTrue(
            "tool section must restrain casual calls",
            withTools.contains("Only call a tool when the user clearly asks")
        )

        val withoutTools = promptManager.buildPrompt(includeTools = false).toString()
        assertFalse(
            "streaming prompt must not mention tools",
            withoutTools.contains("Only call a tool when the user clearly asks")
        )
    }
}
