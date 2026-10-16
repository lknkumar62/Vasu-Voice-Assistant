package com.vasu.assistant

import com.vasu.assistant.core.ai.ToolDefinition
import com.vasu.assistant.core.ai.ToolRelevanceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the tool-relevance pre-filter: windowing, whole-word matching,
 * URL detection, fail-open narrowing, and Hindi keyword coverage.
 * Pure JVM, no Android dependencies.
 */
class ToolRelevanceFilterTest {

    private val all: List<ToolDefinition> = listOf(
        ToolDefinition("turn_on_torch", "Turn on the device flashlight"),
        ToolDefinition("turn_off_torch", "Turn off the device flashlight"),
        ToolDefinition("volume_up", "Increase device media volume"),
        ToolDefinition("volume_down", "Decrease device media volume"),
        ToolDefinition("set_volume", "Set device volume to a percentage"),
        ToolDefinition("battery_info", "Check battery level and charging status"),
        ToolDefinition("device_info", "Get hardware and OS specs"),
        ToolDefinition("storage_info", "Check storage usage"),
        ToolDefinition("open_whatsapp", "Open WhatsApp"),
        ToolDefinition("make_call", "Make a phone call"),
        ToolDefinition("send_message", "Send SMS or WhatsApp message"),
        ToolDefinition("lookup_contact", "Lookup contact by name"),
        ToolDefinition("media_play_pause", "Play or pause media"),
        ToolDefinition("media_next", "Play next track"),
        ToolDefinition("take_photo", "Take a photo"),
        ToolDefinition("search_web", "Search the web"),
        ToolDefinition("read_screen", "Read current screen content"),
        ToolDefinition("click", "Click on screen element"),
        ToolDefinition("type_text", "Type text into input field"),
        ToolDefinition("press_back", "Press back button"),
        ToolDefinition("press_home", "Press home button"),
        ToolDefinition("scroll_down", "Scroll down"),
        ToolDefinition("scroll_up", "Scroll up"),
        ToolDefinition("open_app", "Open an app by name"),
        ToolDefinition("ocr_extract", "Extract text from screen using OCR"),
        ToolDefinition("describe_image", "Describe objects in a photo"),
        ToolDefinition("scan_qr", "Read the QR code in a photo"),
        ToolDefinition("browse_files", "Browse files in a folder"),
        ToolDefinition("search_files", "Search for a file"),
        ToolDefinition("delete_file", "Delete a file"),
        ToolDefinition("create_alarm", "Create an alarm"),
        ToolDefinition("run_mission", "Run a saved mission"),
        ToolDefinition("read_notifications", "Read notifications")
    )
    private val allNames: Set<String> = all.map { it.name }.toSet()

    @Test
    fun `window keeps only the newest 8000 chars`() {
        val oldKeyword = "torch " + "x".repeat(9000)
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf(oldKeyword, "hello there"), allNames
        )
        assertTrue("stale torch signal past the window must be dropped", selected.isEmpty())
    }

    @Test
    fun `window retains a recent keyword past old filler`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("x".repeat(9000), "please turn on the torch"), allNames
        )
        assertTrue(selected.contains("turn_on_torch"))
    }

    @Test
    fun `update does not match date`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("please update the app"), allNames
        )
        assertFalse(selected.contains("create_alarm"))
    }

    @Test
    fun `sometimes does not match time`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("sometimes it rains"), allNames
        )
        assertFalse(selected.contains("create_alarm"))
    }

    @Test
    fun `pasted URL triggers the web group`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("summarise this article https://example.com/post/1"), allNames
        )
        assertTrue(selected.contains("search_web"))
    }

    @Test
    fun `bare domain triggers the web group`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("go to example.org and tell me what it says"), allNames
        )
        assertTrue(selected.contains("search_web"))
    }

    @Test
    fun `no signal returns empty wanted set`() {
        val selected = ToolRelevanceFilter.selectForConversation(listOf("hi"), allNames)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `empty wanted fails open to the full list`() {
        val decided = ToolRelevanceFilter.decide(all, emptySet())
        assertEquals(all.size, decided.size)
        assertEquals(all.map { it.name }, decided.map { it.name })
    }

    @Test
    fun `filtered subset is non-empty and narrower than full list`() {
        val decided = ToolRelevanceFilter.decide(all, setOf("turn_on_torch", "turn_off_torch"))
        assertTrue(decided.isNotEmpty())
        assertTrue(decided.size < all.size)
        assertTrue(decided.map { it.name }.contains("turn_on_torch"))
    }

    @Test
    fun `filtering to nothing fails open to the full list`() {
        val decided = ToolRelevanceFilter.decide(all, setOf("no_such_tool"))
        assertEquals(all.size, decided.size)
    }

    @Test
    fun `hindi torch keyword matches`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("टॉर्च चालू करो"), allNames
        )
        assertTrue(selected.contains("turn_on_torch"))
    }

    @Test
    fun `hindi volume keyword matches`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("आवाज़ बढ़ाओ"), allNames
        )
        assertTrue(selected.any { it == "volume_up" || it == "volume_down" || it == "set_volume" })
    }

    @Test
    fun `hindi call keyword triggers messaging tools`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("papa ko कॉल करो"), allNames
        )
        assertTrue(selected.contains("make_call"))
    }

    @Test
    fun `hindi message keyword triggers messaging tools`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("उसे संदेश भेजो"), allNames
        )
        assertTrue(selected.contains("send_message"))
    }

    @Test
    fun `hindi open keyword triggers screen tools`() {
        val selected = ToolRelevanceFilter.selectForConversation(
            listOf("यूट्यूब खोलो"), allNames
        )
        assertTrue(selected.contains("open_app"))
    }
}
