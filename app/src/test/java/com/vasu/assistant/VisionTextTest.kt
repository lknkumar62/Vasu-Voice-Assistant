package com.vasu.assistant

import com.vasu.assistant.core.ocr.DetectedItem
import com.vasu.assistant.core.ocr.LabelScore
import com.vasu.assistant.core.ocr.VisionText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the vision summaries: nothing detected must not read as a success, and
 * every confidence shown comes from the value ML Kit returned.
 */
class VisionTextTest {

    private fun item(vararg labels: LabelScore, top: Int = 0) = DetectedItem(
        labels = labels.toList(),
        left = 0,
        top = top,
        right = 100,
        bottom = top + 100
    )

    @Test
    fun `nothing detected produces no description`() {
        assertNull(VisionText.describeItems(emptyList()))
    }

    @Test
    fun `top label is reported with the real confidence`() {
        val description = VisionText.describeItems(
            listOf(item(LabelScore("plant", 0.91f), LabelScore("food", 0.2f)))
        )

        assertEquals("Detected: plant (91%)", description)
    }

    @Test
    fun `unclassified objects are named honestly`() {
        val description = VisionText.describeItems(listOf(item()))

        assertEquals("Detected: an unclassified object", description)
    }

    @Test
    fun `object list beyond the cap is counted not silently cut`() {
        val items = (0 until 6).map { index ->
            item(LabelScore("object$index", 0.5f), top = index * 10)
        }

        val description = VisionText.describeItems(items)!!

        assertTrue(description.startsWith("Detected 6 objects: "))
        assertTrue(description.contains("object3"))
        assertFalse(description.contains("object4"))
    }

    @Test
    fun `confidence is rounded and clamped into a percent`() {
        assertEquals(50, VisionText.percent(0.5f))
        assertEquals(88, VisionText.percent(0.876f))
        assertEquals(100, VisionText.percent(1.5f))
        assertEquals(0, VisionText.percent(-0.2f))
    }

    @Test
    fun `missing qr values yield no summary`() {
        assertNull(VisionText.qrSummary(emptyList()))
        assertNull(VisionText.qrSummary(listOf("", "   ")))
    }

    @Test
    fun `single qr value is the summary`() {
        assertEquals("https://vasu.app", VisionText.qrSummary(listOf("https://vasu.app")))
    }

    @Test
    fun `extra qr codes are noted after the first`() {
        assertEquals(
            "https://a (and 1 more QR code)",
            VisionText.qrSummary(listOf("https://a", "https://b"))
        )
        assertEquals(
            "https://a (and 2 more QR codes)",
            VisionText.qrSummary(listOf("https://a", "https://b", "https://c"))
        )
    }
}
