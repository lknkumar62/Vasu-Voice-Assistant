package com.vasu.assistant

import com.vasu.assistant.core.command.LocalCommandRouter
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A2 (command-router): JVM-only tests for the local-first deterministic
 * router. No Android dependencies — pure [LocalCommandRouter.match] contract:
 * EN + Hindi positives, ambiguity/compound/secondary-verb negatives,
 * case-insensitivity, and requiredRole sanity against SecurityTypes.
 */
class LocalCommandRouterTest {

    // ------------------------------------------------------------ torch on/off

    @Test
    fun `torch on EN maps to turn_on_torch`() {
        val cmd = LocalCommandRouter.match("Turn on the torch")
        assertNotNull(cmd)
        assertEquals("turn_on_torch", cmd!!.toolName)
        assertTrue(cmd.params.isEmpty())
        assertEquals("Torch is now on.", cmd.reply("Torch switched on"))
    }

    @Test
    fun `torch on is case insensitive and strips leading fillers`() {
        assertEquals("turn_on_torch", LocalCommandRouter.match("TORCH ON")?.toolName)
        assertEquals("turn_on_torch", LocalCommandRouter.match("please turn on the torch")?.toolName)
        assertEquals("turn_on_torch", LocalCommandRouter.match("Hey Vasu, torch on")?.toolName)
        assertEquals("turn_on_torch", LocalCommandRouter.match("vasu flashlight jalao")?.toolName)
    }

    @Test
    fun `torch on Hindi roman maps with roman reply`() {
        val cmd = LocalCommandRouter.match("torch chalu karo")
        assertNotNull(cmd)
        assertEquals("turn_on_torch", cmd!!.toolName)
        assertEquals("Ji, torch on kar di gayi hai.", cmd.reply("ok"))
    }

    @Test
    fun `torch on Devanagari maps with devanagari reply`() {
        val cmd = LocalCommandRouter.match("टॉर्च चालू करो")
        assertNotNull(cmd)
        assertEquals("turn_on_torch", cmd!!.toolName)
        assertEquals("जी, टॉर्च ऑन कर दी गई है।", cmd.reply("ok"))
    }

    @Test
    fun `torch off EN and Hindi map to turn_off_torch`() {
        assertEquals("turn_off_torch", LocalCommandRouter.match("switch off the torch")?.toolName)
        assertEquals("turn_off_torch", LocalCommandRouter.match("flashlight band karo")?.toolName)
        val deva = LocalCommandRouter.match("टॉर्च बंद करो")
        assertNotNull(deva)
        assertEquals("turn_off_torch", deva!!.toolName)
        assertEquals("जी, टॉर्च बंद कर दी गई है।", deva.reply("ok"))
    }

    // ------------------------------------------------------------------ volume

    @Test
    fun `volume up EN and Hindi map to volume_up`() {
        assertEquals("volume_up", LocalCommandRouter.match("volume up")?.toolName)
        assertEquals("volume_up", LocalCommandRouter.match("please increase the volume")?.toolName)
        val roman = LocalCommandRouter.match("awaz badhao")
        assertNotNull(roman)
        assertEquals("volume_up", roman!!.toolName)
        assertEquals("Ji, awaaz badha di gayi hai.", roman.reply("ok"))
        assertEquals("volume_up", LocalCommandRouter.match("आवाज़ बढ़ाओ")?.toolName)
    }

    @Test
    fun `volume down EN and Hindi map to volume_down`() {
        assertEquals("volume_down", LocalCommandRouter.match("volume down")?.toolName)
        val roman = LocalCommandRouter.match("volume kam karo")
        assertNotNull(roman)
        assertEquals("volume_down", roman!!.toolName)
        assertEquals("Ji, awaaz kam kar di gayi hai.", roman.reply("ok"))
    }

    @Test
    fun `mute maps to set_volume zero`() {
        val cmd = LocalCommandRouter.match("mute the sound")
        assertNotNull(cmd)
        assertEquals("set_volume", cmd!!.toolName)
        assertEquals(0, cmd.params["level"])
        assertEquals("Sound muted.", cmd.reply("Volume set to 0%"))
    }

    @Test
    fun `mute Hindi maps to set_volume zero with roman reply`() {
        val cmd = LocalCommandRouter.match("awaaz band karo")
        assertNotNull(cmd)
        assertEquals("set_volume", cmd!!.toolName)
        assertEquals(0, cmd.params["level"])
        assertEquals("Ji, awaaz mute kar di gayi hai.", cmd.reply("ok"))
    }

    @Test
    fun `max volume maps to set_volume hundred`() {
        val cmd = LocalCommandRouter.match("set volume to max")
        assertNotNull(cmd)
        assertEquals("set_volume", cmd!!.toolName)
        assertEquals(100, cmd.params["level"])
        assertEquals("Volume set to maximum.", cmd.reply("Volume set to 100%"))
        assertEquals("set_volume", LocalCommandRouter.match("volume full karo")?.toolName)
    }

    // ---------------------------------------------------------- battery/time/date

    @Test
    fun `battery EN and Hindi map to battery_info`() {
        assertEquals("battery_info", LocalCommandRouter.match("battery kitni hai")?.toolName)
        assertEquals("battery_info", LocalCommandRouter.match("what is my battery level")?.toolName)
        assertEquals("battery_info", LocalCommandRouter.match("बैटरी कितनी है")?.toolName)
    }

    @Test
    fun `battery reply surfaces the tool result level`() {
        val cmd = LocalCommandRouter.match("what is my battery level")
        assertNotNull(cmd)
        assertTrue(cmd!!.reply("Battery at 72%, charging").contains("72"))
        val roman = LocalCommandRouter.match("battery kitni hai")!!
        assertTrue(roman.reply("Battery at 72%, charging").contains("72"))
    }

    @Test
    fun `time EN and Hindi map to local_time sentinel with non-blank reply`() {
        val en = LocalCommandRouter.match("what time is it")
        assertNotNull(en)
        assertEquals(LocalCommandRouter.TOOL_GET_TIME, en!!.toolName)
        assertTrue(en.reply("").isNotBlank())

        assertEquals(LocalCommandRouter.TOOL_GET_TIME, LocalCommandRouter.match("time kya hai")?.toolName)
        assertEquals(LocalCommandRouter.TOOL_GET_TIME, LocalCommandRouter.match("samay batao")?.toolName)
        val deva = LocalCommandRouter.match("समय क्या है")
        assertNotNull(deva)
        assertTrue(deva!!.reply("").contains("समय"))
    }

    @Test
    fun `date EN and Hindi map to local_date sentinel with non-blank reply`() {
        val en = LocalCommandRouter.match("what is today's date")
        assertNotNull(en)
        assertEquals(LocalCommandRouter.TOOL_GET_DATE, en!!.toolName)
        assertTrue(en.reply("").isNotBlank())

        assertEquals(LocalCommandRouter.TOOL_GET_DATE, LocalCommandRouter.match("aaj ki tarikh batao")?.toolName)
        assertEquals(LocalCommandRouter.TOOL_GET_DATE, LocalCommandRouter.match("आज की तारीख बताओ")?.toolName)
    }

    // ---------------------------------------------------------------- negatives

    @Test
    fun `ambiguous single words return null`() {
        assertNull(LocalCommandRouter.match("torch"))
        assertNull(LocalCommandRouter.match("Torch"))
        assertNull(LocalCommandRouter.match("volume"))
        assertNull(LocalCommandRouter.match("flashlight"))
        assertNull(LocalCommandRouter.match(""))
        assertNull(LocalCommandRouter.match("   "))
        assertNull(LocalCommandRouter.match("hello vasu"))
    }

    @Test
    fun `compound commands return null`() {
        assertNull(LocalCommandRouter.match("torch on and send sms"))
        assertNull(LocalCommandRouter.match("volume up and torch on"))
        assertNull(LocalCommandRouter.match("torch on then volume up"))
        assertNull(LocalCommandRouter.match("time batao aur sms bhejo"))
        // Hinglish "aur" (= and) joins two commands — routes must not fire half.
        assertNull(LocalCommandRouter.match("go back aur photo dikhao"))
        assertNull(LocalCommandRouter.match("call mom aur weather batao"))
    }

    @Test
    fun `secondary verbs return null`() {
        assertNull(LocalCommandRouter.match("torch on karke photo lo"))
        assertNull(LocalCommandRouter.match("volume badhao aur gana chalao"))
        assertNull(LocalCommandRouter.match("send sms to mom"))
        // "open camera" intentionally flipped by FIX-8: it now routes as
        // open_app("camera") — a true app-open the ToolRouter can resolve.
        assertNull(LocalCommandRouter.match("play some music"))
        assertNull(LocalCommandRouter.match("bluetooth on karo"))
    }

    @Test
    fun `conflicting cues return null`() {
        assertNull(LocalCommandRouter.match("torch on or off"))
        assertNull(LocalCommandRouter.match("volume up or down"))
        // "unmute" must NOT match "mute" (token matching, not substring)
        assertNull(LocalCommandRouter.match("unmute"))
        // "update" must NOT match "date"; "sometimes" must NOT match "time"
        assertNull(LocalCommandRouter.match("update my apps"))
    }

    // ------------------------------------------------------------- role sanity

    @Test
    fun `device commands demand exactly the LOW risk role`() {
        val inputs = listOf(
            "turn on the torch", "torch band karo", "volume up", "awaz kam karo",
            "mute", "volume full", "battery kitni hai", "time kya hai", "aaj ki tarikh"
        )
        for (text in inputs) {
            val cmd = LocalCommandRouter.match(text)
            assertNotNull("expected match for: $text", cmd)
            assertEquals(
                "requiredRole must track RiskLevel.LOW for: $text",
                RiskLevel.LOW.requiredRole,
                cmd!!.requiredRole
            )
        }
        assertEquals(UserRole.GUEST, RiskLevel.LOW.requiredRole)
    }

    // -------------------------------------------------- FIX-8 offline routes

    @Test
    fun `offline routes map to their tools with extracted params`() {
        assertEquals("open_app", LocalCommandRouter.match("open whatsapp")!!.toolName)
        assertEquals("open_app", LocalCommandRouter.match("whatsapp kholo")!!.toolName)
        assertEquals("make_call", LocalCommandRouter.match("call mom")!!.toolName)
        assertEquals("mom", LocalCommandRouter.match("call mom")!!.params["contact"])
        assertEquals("mom", LocalCommandRouter.match("mom ko call karo")!!.params["contact"])
        assertEquals("press_back", LocalCommandRouter.match("go back")!!.toolName)
        assertEquals("press_home", LocalCommandRouter.match("go home")!!.toolName)
        assertEquals("scroll_down", LocalCommandRouter.match("neeche scroll karo")!!.toolName)
        assertEquals("scroll_up", LocalCommandRouter.match("upar scroll karo")!!.toolName)
        assertEquals("click", LocalCommandRouter.match("login pe click karo")!!.toolName)
        assertEquals("login", LocalCommandRouter.match("login pe click karo")!!.params["text"])
    }

    @Test
    fun `offline routes demand the role of their ToolRouter risk`() {
        assertEquals(RiskLevel.LOW.requiredRole, LocalCommandRouter.match("go back")!!.requiredRole)
        assertEquals(RiskLevel.MEDIUM.requiredRole, LocalCommandRouter.match("open whatsapp")!!.requiredRole)
        assertEquals(RiskLevel.MEDIUM.requiredRole, LocalCommandRouter.match("click login")!!.requiredRole)
        assertEquals(RiskLevel.HIGH.requiredRole, LocalCommandRouter.match("call mom")!!.requiredRole)
        assertEquals(UserRole.FRIEND, RiskLevel.MEDIUM.requiredRole)
        assertEquals(UserRole.BOSS, RiskLevel.HIGH.requiredRole)
    }

    @Test
    fun `ambiguity and cross-intent routes return null`() {
        assertNull(LocalCommandRouter.match("open"))           // no app name
        assertNull(LocalCommandRouter.match("call"))           // no contact
        assertNull(LocalCommandRouter.match("click"))          // no target
        assertNull(LocalCommandRouter.match("scroll"))         // no direction
        assertNull(LocalCommandRouter.match("scroll up down")) // both directions
        assertNull(LocalCommandRouter.match("back home"))      // two navigations
        assertNull(LocalCommandRouter.match("call back mom"))  // return-call, not press_back
        assertNull(LocalCommandRouter.match("call mom volume up"))    // device+call mix
        assertNull(LocalCommandRouter.match("open whatsapp call mom")) // two routes
    }
}
