package com.vasu.assistant

import com.vasu.assistant.core.ai.HindiResponseNormalizer
import com.vasu.assistant.core.automation.ActionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the script-mirroring contract of the normalizer:
 * Roman user input must never come back forced into Devanagari.
 */
class ResponseScriptMirrorTest {

    private val normalizer = HindiResponseNormalizer()
    private val devanagari = Regex("[\\u0900-\\u097F]")

    @Test
    fun `roman greeting gets roman reply not devanagari`() {
        val reply = normalizer.getConversationalResponse("Hello Vasu kya hal hai")
        assertTrue(reply != null)
        assertFalse(devanagari.containsMatchIn(reply!!))
    }

    @Test
    fun `devanagari greeting gets devanagari reply`() {
        // "नमस्ते" hits the exact-greeting branch: must be non-null Devanagari.
        val reply = normalizer.getConversationalResponse("नमस्ते")
        assertTrue(reply != null)
        assertTrue(devanagari.containsMatchIn(reply!!))
    }

    @Test
    fun `name query mirrors script`() {
        val roman = normalizer.getConversationalResponse("tumhara naam kya hai")
        assertTrue(roman != null)
        assertFalse(devanagari.containsMatchIn(roman!!))

        val english = normalizer.getConversationalResponse("what is your name?")
        assertTrue(english != null)
        assertFalse(devanagari.containsMatchIn(english!!))
        assertTrue(english.contains("VASU"))

        val deva = normalizer.getConversationalResponse("तुम्हारा नाम क्या है")
        assertTrue(deva != null)
        assertTrue(devanagari.containsMatchIn(deva!!))
    }

    @Test
    fun `normalize preserves roman response for roman input`() {
        val out = normalizer.normalize("Main bilkul theek hoon, batao kya chal raha hai?", "hello vasu kya hal hai")
        assertFalse(devanagari.containsMatchIn(out))
    }

    @Test
    fun `normalize preserves english response for english input`() {
        val out = normalizer.normalize("I am doing well. How can I help?", "Hello Vasu, how are you?")
        assertFalse(devanagari.containsMatchIn(out))
    }

    @Test
    fun `torch confirmation mirrors roman script`() {
        val result = ActionResult.success("torch", "Torch on")
        val out = normalizer.describeActionResult(result, "Vasu torch on karo")
        assertFalse(devanagari.containsMatchIn(out))
        assertTrue(out.contains("torch", ignoreCase = true))
    }

    @Test
    fun `torch confirmation stays devanagari for devanagari input`() {
        val result = ActionResult.success("torch", "Torch on")
        val out = normalizer.describeActionResult(result, "टॉर्च ऑन करो")
        assertTrue(devanagari.containsMatchIn(out))
    }
}
