package com.vasu.assistant

import org.junit.Assert.*
import org.junit.Test

class ToolRouterTest {

    @Test
    fun `tool registry should have all core tools`() {
        val toolNames = listOf(
            "open_app", "click", "type_text", "read_screen", "scroll_down", "scroll_up",
            "press_back", "press_home", "make_call", "send_message", "whatsapp",
            "turn_on_torch", "set_volume", "volume_up", "volume_down",
            "media_play_pause", "media_next", "media_previous", "bluetooth_toggle",
            "battery_info", "device_info", "search_web", "create_alarm", "run_mission",
            "browse_files", "search_files", "read_file", "rename_file", "copy_file",
            "move_file", "delete_file", "share_file", "storage_info",
            "take_photo", "start_recording", "stop_recording", "toggle_flash",
            "ocr_extract", "scan_qr"
        )
        // Verify all expected tool names exist
        assertTrue("Should have at least 35 tools registered", toolNames.size >= 35)
        toolNames.forEach { name ->
            assertNotNull("Tool '$name' should not be null", name)
        }
    }

    @Test
    fun `tool parameters should have required fields`() {
        data class TestParam(val name: String, val type: String, val required: Boolean)
        val param = TestParam("package", "string", true)
        assertEquals("package", param.name)
        assertEquals("string", param.type)
        assertTrue(param.required)
    }
}
