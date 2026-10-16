package com.vasu.assistant

import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.voice.GeminiLiveProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-6: voice catalog invariants — the Jarvis/Nova gender presets must cover the
 * whole Gemini voice list exactly once and must resolve within their own set.
 */
class VoiceSettingsCatalogTest {

    @Test
    fun genderSetsCoverVoiceCatalogWithoutOverlap() {
        val male = VasuSettings.MALE_GEMINI_VOICES
        val female = VasuSettings.FEMALE_GEMINI_VOICES
        assertTrue("Voice sets must not overlap", male.intersect(female).isEmpty())
        assertEquals(
            "Every catalog voice must have exactly one gender",
            VasuSettings.GEMINI_VOICES.toSet(),
            male + female
        )
    }

    @Test
    fun genderPresetsBelongToTheirOwnSet() {
        assertTrue(VasuSettings.JARVIS_GEMINI_VOICE in VasuSettings.MALE_GEMINI_VOICES)
        assertTrue(VasuSettings.NOVA_GEMINI_VOICE in VasuSettings.FEMALE_GEMINI_VOICES)
    }

    @Test
    fun genderForVoiceResolvesEveryCatalogVoice() {
        assertEquals(
            VasuSettings.VOICE_GENDER_MALE,
            VasuSettings.genderForVoice(VasuSettings.JARVIS_GEMINI_VOICE)
        )
        assertEquals(
            VasuSettings.VOICE_GENDER_FEMALE,
            VasuSettings.genderForVoice(VasuSettings.NOVA_GEMINI_VOICE)
        )
        VasuSettings.GEMINI_VOICES.forEach { voice ->
            assertTrue("Catalog voice $voice has no gender", VasuSettings.genderForVoice(voice) != null)
        }
    }

    @Test
    fun geminiVoiceForGenderMapsBothGenders() {
        assertEquals(
            VasuSettings.JARVIS_GEMINI_VOICE,
            VasuSettings.geminiVoiceForGender(VasuSettings.VOICE_GENDER_MALE)
        )
        assertEquals(
            VasuSettings.NOVA_GEMINI_VOICE,
            VasuSettings.geminiVoiceForGender(VasuSettings.VOICE_GENDER_FEMALE)
        )
        // Unknown input falls back to the female preset (the current default voice).
        assertEquals(
            VasuSettings.NOVA_GEMINI_VOICE,
            VasuSettings.geminiVoiceForGender("unknown")
        )
    }

    @Test
    fun liveModelChoicesMatchProtocolDefaults() {
        val ids = VasuSettings.GEMINI_LIVE_MODELS.map { it.first }
        assertTrue(GeminiLiveProtocol.LIVE_MODEL in ids)
        assertTrue(GeminiLiveProtocol.LIVE_MODEL_FALLBACK in ids)
        assertEquals(VasuSettings.DEFAULT_GEMINI_LIVE_MODEL, ids.first())
    }
}
