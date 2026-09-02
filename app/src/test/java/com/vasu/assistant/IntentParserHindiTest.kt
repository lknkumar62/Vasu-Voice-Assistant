package com.vasu.assistant

import com.vasu.assistant.core.ai.IntentParser
import com.vasu.assistant.core.ai.IntentType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IntentParserHindiTest {

    private lateinit var intentParser: IntentParser

    @Before
    fun setUp() {
        intentParser = IntentParser()
    }

    @Test
    fun `parses torch command in hindi devanagari and roman hindi`() {
        val devanagariIntent = intentParser.parse("टॉर्च चालू करो")
        assertEquals(IntentType.TOGGLE_TORCH, devanagariIntent.intent)

        val romanIntent = intentParser.parse("torch on karo")
        assertEquals(IntentType.TOGGLE_TORCH, romanIntent.intent)
    }

    @Test
    fun `parses volume command in hindi`() {
        val intent = intentParser.parse("वॉल्यूम 80 करो")
        assertEquals(IntentType.SET_VOLUME, intent.intent)
        assertEquals("80", intent.entities["level"])
    }

    @Test
    fun `parses app launch in hindi`() {
        val intent = intentParser.parse("व्हाट्सएप खोलो")
        assertEquals(IntentType.OPEN_APP, intent.intent)
    }

    @Test
    fun `parses alarm command in hindi`() {
        val intent = intentParser.parse("सुबह 7:00 बजे का अलार्म लगाओ")
        assertEquals(IntentType.CREATE_ALARM, intent.intent)
        assertEquals("7:00", intent.entities["time"])
    }
}
