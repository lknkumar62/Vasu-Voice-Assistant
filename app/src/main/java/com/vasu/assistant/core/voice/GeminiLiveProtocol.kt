package com.vasu.assistant.core.voice

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Pure JSON payload builders for the Gemini Live WebSocket protocol (FIX-12).
 *
 * Shapes follow the official raw-WebSocket guide (ai.google.dev, 2026-09):
 * - Setup: `{setup: {model, generationConfig{responseModalities, speechConfig}, systemInstruction}}`
 * - Audio in: `{realtimeInput: {audio: {data: <base64 PCM16/16kHz>, mimeType: "audio/pcm;rate=16000"}}}`
 * - Text in: `{realtimeInput: {text: "..."}}`
 *
 * Kept free of Android APIs (java.util.Base64, org.json) so they are
 * JVM-unit-testable.
 */
object GeminiLiveProtocol {

    const val WS_ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /** Current recommended Live model (docs quickstart, 2026-09). */
    const val LIVE_MODEL = "models/gemini-3.8-live"

    /** GA native-audio Live model, used as a second connection attempt. */
    const val LIVE_MODEL_FALLBACK = "models/gemini-live-2.5-flash-native-audio"

    const val TARGET_VOICE = "Kore"

    const val AUDIO_MIME_TYPE = "audio/pcm;rate=16000"

    /** Initial session configuration; must be the first message on the socket. */
    fun setupMessage(model: String, voiceName: String, systemInstruction: String): String {
        val setup = JSONObject()
        setup.put("model", model)

        val generationConfig = JSONObject()
        val responseModalities = JSONArray()
        responseModalities.put("AUDIO")
        generationConfig.put("responseModalities", responseModalities)

        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", voiceName)
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        generationConfig.put("speechConfig", speechConfig)

        setup.put("generationConfig", generationConfig)

        if (systemInstruction.isNotBlank()) {
            val systemInstructionObj = JSONObject()
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", systemInstruction)
            parts.put(part)
            systemInstructionObj.put("parts", parts)
            setup.put("systemInstruction", systemInstructionObj)
        }

        val root = JSONObject()
        root.put("setup", setup)
        return root.toString()
    }

    /** One chunk of raw 16-bit PCM @16kHz mono, base64-encoded. */
    fun audioChunkMessage(pcmChunk: ByteArray): String {
        val root = JSONObject()
        val realtimeInput = JSONObject()
        val audio = JSONObject()
        audio.put("data", Base64.getEncoder().encodeToString(pcmChunk))
        audio.put("mimeType", AUDIO_MIME_TYPE)
        realtimeInput.put("audio", audio)
        root.put("realtimeInput", realtimeInput)
        return root.toString()
    }

    /** Text turn sent as realtimeInput.text (documented "Send text" shape). */
    fun textTurnMessage(text: String): String {
        val root = JSONObject()
        val realtimeInput = JSONObject()
        realtimeInput.put("text", text)
        root.put("realtimeInput", realtimeInput)
        return root.toString()
    }
}
