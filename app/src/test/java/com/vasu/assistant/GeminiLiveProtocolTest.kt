package com.vasu.assistant

import com.vasu.assistant.core.voice.GeminiLiveProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveProtocolTest {

    @Test
    fun endpointUsesCurrentV1BetaBidiEndpoint() {
        assertTrue(GeminiLiveProtocol.WS_ENDPOINT.contains("v1beta.GenerativeService.BidiGenerateContent"))
        assertFalse(GeminiLiveProtocol.WS_ENDPOINT.contains("v1alpha"))
    }

    @Test
    fun liveModelIsNotTheRetired2_0FlashExp() {
        assertTrue(GeminiLiveProtocol.LIVE_MODEL.startsWith("models/"))
        assertFalse(GeminiLiveProtocol.LIVE_MODEL.contains("2.0-flash-exp"))
        assertFalse(GeminiLiveProtocol.LIVE_MODEL_FALLBACK.contains("2.0-flash-exp"))
    }

    @Test
    fun audioChunkUsesRealtimeInputAudioShape() {
        val root = JSONObject(GeminiLiveProtocol.audioChunkMessage(byteArrayOf(1, 2, 3, 4)))
        val audio = root.getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertEquals("AQIDBA==", audio.getString("data"))
    }

    @Test
    fun setupMessageCarriesModelVoiceAndInstruction() {
        val root = JSONObject(
            GeminiLiveProtocol.setupMessage(GeminiLiveProtocol.LIVE_MODEL, "Kore", "Be helpful")
        )
        val setup = root.getJSONObject("setup")
        assertEquals(GeminiLiveProtocol.LIVE_MODEL, setup.getString("model"))

        val generationConfig = setup.getJSONObject("generationConfig")
        assertEquals("AUDIO", generationConfig.getJSONArray("responseModalities").getString(0))
        assertEquals(
            "Kore",
            generationConfig.getJSONObject("speechConfig")
                .getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig")
                .getString("voiceName")
        )
        assertEquals(
            "Be helpful",
            setup.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        )
    }

    @Test
    fun setupMessageOmitsBlankSystemInstruction() {
        val root = JSONObject(GeminiLiveProtocol.setupMessage(GeminiLiveProtocol.LIVE_MODEL, "Kore", "   "))
        assertFalse(root.getJSONObject("setup").has("systemInstruction"))
    }

    @Test
    fun textTurnUsesRealtimeInputTextShape() {
        val root = JSONObject(GeminiLiveProtocol.textTurnMessage("namaste"))
        assertEquals("namaste", root.getJSONObject("realtimeInput").getString("text"))
    }
}
