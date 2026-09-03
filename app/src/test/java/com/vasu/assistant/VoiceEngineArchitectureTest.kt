package com.vasu.assistant

import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.stt.SttErrorKind
import com.vasu.assistant.core.tts.ActiveVoiceSource
import com.vasu.assistant.core.tts.VoiceEngine
import com.vasu.assistant.core.tts.toSpeakableText
import com.vasu.assistant.ui.voice.VoiceUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the Voice Engine architecture, prioritization, and settings.
 */
class VoiceEngineArchitectureTest {

    @Test
    fun `default gemini voice is natural female Kore`() {
        assertEquals("Kore", VasuSettings.DEFAULT_GEMINI_TTS_VOICE)
    }

    @Test
    fun `default gemini tts model is 3_1 flash tts preview`() {
        assertEquals("gemini-3.1-flash-tts-preview", VasuSettings.DEFAULT_GEMINI_TTS_MODEL)
    }

    @Test
    fun `gemini tts model fallback hierarchy exists`() {
        assertEquals("gemini-2.5-flash-preview-tts", VasuSettings.FALLBACK_GEMINI_TTS_MODEL)
        assertEquals("gemini-2.0-flash", VasuSettings.BASE_GEMINI_TTS_MODEL)
    }

    @Test
    fun `active voice sources correctly reflect architecture priorities`() {
        val sources = ActiveVoiceSource.values().map { it.name }
        assertTrue(sources.contains("GEMINI_ONLINE"))
        assertTrue(sources.contains("LOCAL_OFFLINE"))
        assertTrue(sources.contains("ANDROID_FALLBACK"))
        assertTrue(sources.contains("MUTED"))
    }

    @Test
    fun `voice ui modes cover all required states`() {
        val modes = VoiceUiMode.values().map { it.name }
        assertTrue(modes.contains("LISTENING"))
        assertTrue(modes.contains("PROCESSING"))
        assertTrue(modes.contains("THINKING"))
        assertTrue(modes.contains("SPEAKING"))
        assertTrue(modes.contains("OFFLINE_MODE"))
        assertTrue(modes.contains("GEMINI_UNAVAILABLE"))
        assertTrue(modes.contains("MIC_UNAVAILABLE"))
        assertTrue(modes.contains("PERMISSION_REQUIRED"))
    }

    @Test
    fun `toSpeakableText handles mixed script and clean devanagari`() {
        val input = "VASU: नमस्ते! **VASU** यहाँ है। 😊 https://example.com"
        val speakable = toSpeakableText(input)
        assertFalse(speakable.contains("**"))
        assertFalse(speakable.contains("😊"))
        assertFalse(speakable.contains("https://example.com"))
        assertFalse(speakable.startsWith("VASU:"))
        assertTrue(speakable.contains("नमस्ते!"))
        assertTrue(speakable.contains("यहाँ है।"))
    }

    @Test
    fun `stt error kind mic permission denied requires user intervention`() {
        assertTrue(SttErrorKind.MIC_PERMISSION_DENIED.needsUserAction)
        assertFalse(SttErrorKind.MIC_PERMISSION_DENIED.canRetry)
    }
}
