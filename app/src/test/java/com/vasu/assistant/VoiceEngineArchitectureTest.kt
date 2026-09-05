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

    @Test
    fun `gemini live target voice is Kore`() {
        assertEquals("Kore", com.vasu.assistant.core.voice.GeminiLiveSession.TARGET_VOICE)
    }

    @Test
    fun `gemini live target model is gemini 3_1 flash live preview`() {
        assertEquals("models/gemini-3.1-flash-live-preview", com.vasu.assistant.core.voice.GeminiLiveSession.LIVE_MODEL)
    }

    @Test
    fun `gemini voice states cover all 8 required lifecycle states`() {
        val states = com.vasu.assistant.core.voice.GeminiVoiceState.values().map { it.name }
        assertEquals(8, states.size)
        assertTrue(states.contains("IDLE"))
        assertTrue(states.contains("CONNECTING"))
        assertTrue(states.contains("CONNECTED"))
        assertTrue(states.contains("LISTENING"))
        assertTrue(states.contains("THINKING"))
        assertTrue(states.contains("SPEAKING"))
        assertTrue(states.contains("DISCONNECTED"))
        assertTrue(states.contains("ERROR"))
    }

    @Test
    fun `live session lifecycle states cover strict connection progression`() {
        val states = com.vasu.assistant.core.voice.LiveSessionState.values().map { it.name }
        assertTrue(states.contains("DISCONNECTED"))
        assertTrue(states.contains("CONNECTING"))
        assertTrue(states.contains("OPEN"))
        assertTrue(states.contains("SESSION_CONFIGURED"))
        assertTrue(states.contains("READY"))
        assertTrue(states.contains("STREAMING"))
        assertTrue(states.contains("ERROR"))
    }

    @Test
    fun `native audio sample rates match gemini live specification`() {
        assertEquals(16000, com.vasu.assistant.core.voice.NativeMicrophoneRecorder.SAMPLE_RATE)
        assertEquals(24000, com.vasu.assistant.core.voice.NativeAudioPlayer.SAMPLE_RATE)
    }

    @Test
    fun `gemini live session initial state is disconnected and not ready`() {
        val session = com.vasu.assistant.core.voice.GeminiLiveSession()
        assertEquals(com.vasu.assistant.core.voice.LiveSessionState.DISCONNECTED, session.sessionState.value)
        assertFalse(session.isReady())
    }

    @Test
    fun `native audio player initial state is not playing`() {
        val player = com.vasu.assistant.core.voice.NativeAudioPlayer()
        assertFalse(player.isPlaying.value)
    }
}
