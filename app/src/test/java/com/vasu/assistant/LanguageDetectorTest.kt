package com.vasu.assistant

import com.vasu.assistant.core.ai.DetectedStyle
import com.vasu.assistant.core.ai.LanguageDetector
import com.vasu.assistant.core.ai.ResponseScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the language/script mirroring contract:
 * Roman in -> Roman instruction, Devanagari in -> Devanagari, English stays English.
 */
class LanguageDetectorTest {

    @Test
    fun `roman hindi greeting is detected as roman`() {
        val detected = LanguageDetector.detect("Hello Vasu kya hal hai")
        assertEquals(DetectedStyle.ROMAN_HINDI, detected.style)
        assertEquals(ResponseScript.ROMAN, detected.script)
        assertTrue(detected.instruction.contains("Latin"))
        assertTrue(detected.instruction.contains("Do not use Devanagari"))
    }

    @Test
    fun `devanagari input is detected as devanagari hindi`() {
        val detected = LanguageDetector.detect("हेलो वासु क्या हाल है")
        assertEquals(DetectedStyle.DEVANAGARI_HINDI, detected.style)
        assertEquals(ResponseScript.DEVANAGARI, detected.script)
        assertTrue(detected.instruction.contains("Devanagari"))
    }

    @Test
    fun `plain english with tech terms stays english`() {
        val detected = LanguageDetector.detect("Hello Vasu, explain Kotlin coroutines.")
        assertEquals(DetectedStyle.ENGLISH, detected.style)
        assertEquals(ResponseScript.ENGLISH, detected.script)
        assertTrue(detected.instruction.contains("Reply in English"))
    }

    @Test
    fun `roman hinglish with tech terms is hinglish not english`() {
        val detected = LanguageDetector.detect("Vasu mujhe Kotlin coroutines samjhao.")
        assertTrue(
            detected.style == DetectedStyle.HINGLISH || detected.style == DetectedStyle.ROMAN_HINDI
        )
        assertEquals(ResponseScript.ROMAN, detected.script)
    }

    @Test
    fun `mixed hinglish device command is roman`() {
        val detected = LanguageDetector.detect("Vasu aaj mujhe Android app ka code fix karna hai")
        assertEquals(ResponseScript.ROMAN, detected.script)
    }

    @Test
    fun `torch commands keep user script`() {
        val roman = LanguageDetector.detect("Vasu torch on karo")
        assertEquals(ResponseScript.ROMAN, roman.script)
        val deva = LanguageDetector.detect("टॉर्च ऑन करो")
        assertEquals(DetectedStyle.DEVANAGARI_HINDI, deva.style)
    }

    @Test
    fun `english article the does not trigger hindi`() {
        assertEquals(DetectedStyle.ENGLISH, LanguageDetector.detect("What is the time?").style)
        assertEquals(DetectedStyle.ENGLISH, LanguageDetector.detect("What is the battery level?").style)
        assertEquals(DetectedStyle.ENGLISH, LanguageDetector.detect("Explain the main function.").style)
    }

    @Test
    fun `roman still detected without removed singletons`() {
        // "main/theek/hoon" companions carry the signal after "main" removal.
        assertEquals(ResponseScript.ROMAN, LanguageDetector.detect("main theek hoon").script)
        assertEquals(ResponseScript.ROMAN, LanguageDetector.detect("Vasu torch on karo").script)
    }

    @Test
    fun `empty input is other`() {
        val detected = LanguageDetector.detect("   ")
        assertEquals(DetectedStyle.OTHER, detected.style)
    }

    @Test
    fun `instructions always preserve technical terms`() {
        listOf(
            "hello vasu kya hal hai",
            "हेलो वासु क्या हाल है",
            "Hello Vasu, how are you?"
        ).forEach { input ->
            val instruction = LanguageDetector.detect(input).instruction
            assertTrue(instruction.contains("Kotlin"))
            assertTrue(instruction.contains("ADB"))
        }
    }
}
