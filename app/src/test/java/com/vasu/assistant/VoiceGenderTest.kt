package com.vasu.assistant

import com.vasu.assistant.core.tts.isFemaleVoiceName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VASU speaks as a girl, so she picks a female voice when the engine labels one.
 * These tests pin the labelling rule, because the failure mode is silent: a wrong
 * match makes the app claim a female voice the user cannot hear.
 */
class VoiceGenderTest {

    @Test
    fun `explicit female labels are recognised`() {
        assertTrue(isFemaleVoiceName("hi-IN-female"))
        assertTrue(isFemaleVoiceName("en-IN-default-female"))
        assertTrue(isFemaleVoiceName("hi_IN_f_variant"))
        assertTrue(isFemaleVoiceName("en-us-x-f-local"))
    }

    @Test
    fun `labelling is case insensitive`() {
        assertTrue(isFemaleVoiceName("hi-IN-FEMALE"))
        assertTrue(isFemaleVoiceName("Hi-IN-Female"))
    }

    /**
     * The trap: "female" contains "male", so any male check that runs first, or any
     * substring test written the other way round, silently matches female voices.
     */
    @Test
    fun `male voices are never treated as female`() {
        assertFalse(isFemaleVoiceName("hi-IN-male"))
        assertFalse(isFemaleVoiceName("en-IN-default-male"))
        assertFalse(isFemaleVoiceName("hi_IN_m_variant"))
    }

    /**
     * Google encodes gender in an undocumented triplet. Matching those would be a
     * coin flip reported as a fact, so they must fall through to UNLABELLED.
     */
    @Test
    fun `google triplet ids are not guessed`() {
        assertFalse(isFemaleVoiceName("hi-in-x-hia-local"))
        assertFalse(isFemaleVoiceName("hi-in-x-hie-network"))
        assertFalse(isFemaleVoiceName("en-us-x-sfg-local"))
        assertFalse(isFemaleVoiceName("en-in-x-ene-local"))
    }

    @Test
    fun `unlabelled and empty names are not female`() {
        assertFalse(isFemaleVoiceName(""))
        assertFalse(isFemaleVoiceName("hi"))
        assertFalse(isFemaleVoiceName("en-us"))
        assertFalse(isFemaleVoiceName("hi-IN-language"))
    }
}
