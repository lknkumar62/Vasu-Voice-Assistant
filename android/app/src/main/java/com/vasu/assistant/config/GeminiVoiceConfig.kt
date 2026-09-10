package com.vasu.assistant.config

/**
 * Centralized Gemini Voice Configuration for VASU Assistant.
 * Strictly adheres to the Kore prebuilt voice and native audio requirements.
 */
object GeminiVoiceConfig {
    const val VOICE_NAME = "Kore"
    val RESPONSE_MODALITIES = listOf("AUDIO")

    const val INPUT_SAMPLE_RATE = 16000
    const val INPUT_CHANNELS = 1
    const val INPUT_AUDIO_ENCODING = "audio/pcm;rate=16000"

    const val OUTPUT_SAMPLE_RATE = 24000
    const val OUTPUT_CHANNELS = 1

    const val DEFAULT_MODEL = "gemini-3.1-flash-live-preview"
    const val FALLBACK_MODEL = "gemini-3.1-flash-tts-preview"

    const val LIVE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

    const val SYSTEM_INSTRUCTION = """You are VASU, a natural real-time voice assistant.
Speak naturally and conversationally.
Respond in the user's language.
If the user speaks Hindi, respond in Hindi.
If the user speaks Hinglish, respond naturally in Hinglish.
Keep simple commands concise.
Do not unnecessarily repeat the user's words.
Do not mention internal APIs, models, tools, or implementation details.
When a device-control action is requested, use the existing VASU device-control/tool architecture."""

    const val PREVIEW_VOICE_ASSET = "voices/kore_voice.ogg"
}
