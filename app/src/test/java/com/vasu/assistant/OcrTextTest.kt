package com.vasu.assistant

import com.vasu.assistant.core.ocr.OcrBlock
import com.vasu.assistant.core.ocr.OcrBox
import com.vasu.assistant.core.ocr.OcrScript
import com.vasu.assistant.core.ocr.OcrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure OCR post-processing: script detection, how the Latin and
 * Devanagari recognizer outputs are fused, reading order and the spoken cut.
 * These run on the JVM with no ML Kit or Android types involved.
 */
class OcrTextTest {

    private fun block(
        text: String,
        box: OcrBox,
        source: OcrScript = OcrScript.LATIN
    ) = OcrBlock(
        text = text,
        lines = text.lines(),
        box = box,
        script = OcrText.scriptOf(text),
        source = source
    )

    @Test
    fun `devanagari text is classified as devanagari`() {
        assertEquals(OcrScript.DEVANAGARI, OcrText.scriptOf("नमस्ते वासु क्या हाल है"))
        assertTrue(OcrText.containsDevanagari("अंतिम।"))
        assertTrue(OcrText.containsDevanagari("१२३"))
        assertFalse(OcrText.containsDevanagari("Hello Vasu 123"))
    }

    @Test
    fun `latin text with digits and symbols stays latin`() {
        assertEquals(OcrScript.LATIN, OcrText.scriptOf("Order #42 for Rs. 1,200"))
        assertEquals(OcrScript.LATIN, OcrText.scriptOf("₹1,200 !?"))
        assertTrue(OcrText.containsLatinLetters("VASU"))
        assertFalse(OcrText.containsDevanagari("₹1,200 !?"))
    }

    @Test
    fun `devanagari block evicts overlapping latin noise`() {
        val devanagari = block("नमस्ते", OcrBox(0, 0, 100, 50), source = OcrScript.DEVANAGARI)
        val latinNoise = block("Xqz", OcrBox(5, 5, 95, 45))

        val merged = OcrText.merge(listOf(latinNoise), listOf(devanagari))

        assertEquals(1, merged.size)
        assertEquals("नमस्ते", merged[0].text)
    }

    @Test
    fun `disjoint blocks from both recognizers survive`() {
        val latin = block("Hello", OcrBox(0, 0, 100, 50))
        val devanagari = block("नमस्ते", OcrBox(0, 200, 100, 250), source = OcrScript.DEVANAGARI)

        val merged = OcrText.merge(listOf(latin), listOf(devanagari))

        assertEquals(2, merged.size)
        assertEquals(setOf("Hello", "नमस्ते"), merged.map { it.text }.toSet())
    }

    @Test
    fun `identical text from both recognizers is kept once`() {
        val latin = block("2024", OcrBox(0, 0, 50, 20))
        val devanagariSource = block("2024", OcrBox(0, 0, 50, 20), source = OcrScript.DEVANAGARI)

        val merged = OcrText.merge(listOf(latin), listOf(devanagariSource))

        assertEquals(1, merged.size)
        assertEquals("2024", merged[0].text)
    }

    @Test
    fun `same recognizer repeating a label keeps both occurrences`() {
        val first = block("OK", OcrBox(0, 0, 40, 20))
        val second = block("OK", OcrBox(0, 500, 40, 520))

        val merged = OcrText.merge(listOf(first, second), emptyList())

        assertEquals(2, merged.size)
    }

    @Test
    fun `blocks are ordered top to bottom then left to right`() {
        val bottom = block("footer", OcrBox(0, 900, 100, 950))
        val topRight = block("title", OcrBox(300, 0, 500, 40))
        val topLeft = block("menu", OcrBox(10, 0, 200, 40))

        val ordered = OcrText.readingOrder(listOf(bottom, topRight, topLeft))

        assertEquals(listOf("menu", "title", "footer"), ordered.map { it.text })
    }

    @Test
    fun `document joins blocks in reading order and reports scripts`() {
        val latin = block("Hello", OcrBox(0, 0, 100, 30))
        val devanagari = block("नमस्ते", OcrBox(0, 60, 100, 90), source = OcrScript.DEVANAGARI)

        val document = OcrText.buildDocument(listOf(latin), listOf(devanagari))

        assertEquals("Hello\nनमस्ते", document.fullText)
        assertEquals(setOf(OcrScript.LATIN, OcrScript.DEVANAGARI), document.scripts)
    }

    @Test
    fun `empty inputs produce an empty document`() {
        val document = OcrText.buildDocument(emptyList(), emptyList())

        assertEquals("", document.fullText)
        assertTrue(document.blocks.isEmpty())
        assertTrue(document.scripts.isEmpty())
    }

    @Test
    fun `spoken text is capped with the true total`() {
        val fullText = "a".repeat(1500)

        val spoken = OcrText.formatSpokenText(fullText)

        assertTrue(spoken.startsWith("a".repeat(OcrText.MAX_SPOKEN_CHARS)))
        assertTrue(spoken.contains("1500 characters extracted"))
    }

    @Test
    fun `short spoken text is returned trimmed and unchanged`() {
        assertEquals("Hello Vasu", OcrText.formatSpokenText("  Hello Vasu  "))
    }

    @Test
    fun `box overlap is one for identical and zero for disjoint regions`() {
        val box = OcrBox(0, 0, 100, 50)
        assertEquals(1f, box.intersectionOverUnion(OcrBox(0, 0, 100, 50)), 0.0001f)
        assertEquals(0f, box.intersectionOverUnion(OcrBox(200, 0, 300, 50)), 0.0001f)
        assertEquals(0f, OcrBox.ZERO.intersectionOverUnion(OcrBox.ZERO), 0.0001f)
    }
}
