package com.vasu.assistant

import com.vasu.assistant.core.tts.toSpeakableText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakableTextHindiTest {

    @Test
    fun `preserves devanagari hindi characters and matras`() {
        val input = "नमस्ते! मैं वासु हूँ। क्या हाल-चाल हैं?"
        val output = toSpeakableText(input)
        assertEquals("नमस्ते! मैं वासु हूँ। क्या हाल-चाल हैं?", output)
    }

    @Test
    fun `removes markdown code blocks without breaking surrounding hindi`() {
        val input = """
            यह कोड चलाएँ:
            ```bash
            echo "Hello World"
            ```
            काम पूरा हो गया।
        """.trimIndent()
        val output = toSpeakableText(input)
        assertFalse(output.contains("echo"))
        assertFalse(output.contains("```"))
        assertTrue(output.contains("यह कोड चलाएँ:"))
        assertTrue(output.contains("काम पूरा हो गया।"))
    }

    @Test
    fun `removes urls from speech`() {
        val input = "जानकारी के लिए https://google.com देखें।"
        val output = toSpeakableText(input)
        assertFalse(output.contains("https://google.com"))
        assertTrue(output.contains("जानकारी के लिए"))
        assertTrue(output.contains("देखें।"))
    }

    @Test
    fun `strips emojis but retains hindi text`() {
        val input = "नमस्ते! 😊 बहुत दिनों बाद बात हुई। ❤️ कैसे हो?"
        val output = toSpeakableText(input)
        assertFalse(output.contains("😊"))
        assertFalse(output.contains("❤️"))
        assertEquals("नमस्ते! बहुत दिनों बाद बात हुई। कैसे हो?", output)
    }

    @Test
    fun `strips ui labels`() {
        val input = "VASU: ठीक है, टॉर्च चालू कर दी है।"
        val output = toSpeakableText(input)
        assertEquals("ठीक है, टॉर्च चालू कर दी है।", output)
    }

    @Test
    fun `preserves hindi danda and english punctuation for natural pauses`() {
        val input = "हाँ, बिल्कुल ठीक। बताओ, क्या करना है?"
        val output = toSpeakableText(input)
        assertEquals("हाँ, बिल्कुल ठीक। बताओ, क्या करना है?", output)
    }
}
