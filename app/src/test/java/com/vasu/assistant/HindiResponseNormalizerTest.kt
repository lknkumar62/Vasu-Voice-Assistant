package com.vasu.assistant

import com.vasu.assistant.core.ai.AssistantLanguage
import com.vasu.assistant.core.ai.HindiResponseNormalizer
import com.vasu.assistant.core.automation.ActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HindiResponseNormalizerTest {

    private lateinit var normalizer: HindiResponseNormalizer

    @Before
    fun setUp() {
        normalizer = HindiResponseNormalizer()
    }

    @Test
    fun `test case A - hello maya conversational response`() {
        val response = normalizer.getConversationalResponse("hello maya")
        assertNotNull(response)
        assertTrue(response!!.contains("मैं वासु हूँ"))
        assertTrue(response.contains("माया नहीं"))
    }

    @Test
    fun `test case B - ha thik hai tum batao conversational response`() {
        val response = normalizer.getConversationalResponse("ha thik hai tum batao")
        assertNotNull(response)
        assertTrue(response!!.contains("मैं भी बिल्कुल ठीक हूँ"))
    }

    @Test
    fun `test case C - torch on command action result description`() {
        val actionResult = ActionResult.success("turn_on_torch", "Flashlight turned on")
        val response = normalizer.describeActionResult(actionResult, "torch on karo")
        assertEquals("ठीक है, टॉर्च चालू कर दी है।", response)
    }

    @Test
    fun `test case C2 - torch off command action result description`() {
        val actionResult = ActionResult.success("turn_on_torch", "Flashlight turned off")
        val response = normalizer.describeActionResult(actionResult, "torch off karo")
        assertEquals("टॉर्च बंद कर दी है।", response)
    }

    @Test
    fun `test case D - what is your name default hindi response`() {
        val response = normalizer.getConversationalResponse("what is your name?")
        assertNotNull(response)
        assertEquals("मेरा नाम वासु है। मैं आपकी वॉइस असिस्टेंट हूँ।", response)
    }

    @Test
    fun `test case E - reply in english language switch`() {
        val switchMsg = normalizer.checkLanguageSwitchCommand("reply in English")
        assertNotNull(switchMsg)
        assertEquals(AssistantLanguage.ENGLISH, normalizer.preferredLanguage.value)
        assertEquals("Sure! I will reply in English from now on.", switchMsg)

        val engName = normalizer.getConversationalResponse("what is your name")
        assertEquals("My name is VASU. I am your voice assistant.", engName)

        val switchBack = normalizer.checkLanguageSwitchCommand("reply in Hindi")
        assertNotNull(switchBack)
        assertEquals(AssistantLanguage.HINDI, normalizer.preferredLanguage.value)
    }

    @Test
    fun `device control actions generate natural hindi messages`() {
        val volResult = ActionResult.success("set_volume", "Volume set to 70%")
        val volDesc = normalizer.describeActionResult(volResult)
        assertEquals("वॉल्यूम 70% पर सेट कर दिया गया है।", volDesc)

        val openResult = ActionResult.success("open_app", "Opened WhatsApp")
        val openDesc = normalizer.describeActionResult(openResult)
        assertEquals("WhatsApp खोल दिया गया है।", openDesc)
    }

    @Test
    fun `roman hindi phrases are normalized to devanagari hindi`() {
        val romanInput = "Main theek hoon, aap bataiye kya haal hai?"
        val canonical = normalizer.canonicalize(romanInput)
        assertTrue(canonical.contains("मैं ठीक हूँ"))
        assertTrue(canonical.contains("क्या हाल है"))
    }
}
