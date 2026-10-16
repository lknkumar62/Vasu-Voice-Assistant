package com.vasu.assistant.ui.components

/** Visual states the VASU orb can render. */
enum class OrbState { IDLE, AWAKE, LISTENING, THINKING, SPEAKING }

/**
 * FIX-7: maps the home-screen status flags to an orb state. Priority mirrors the
 * status text on the home card (live actions first, wake word as "awake”).
 */
fun orbStateFor(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    wakeListening: Boolean,
    wakeDetected: Boolean
): OrbState = when {
    isListening -> OrbState.LISTENING
    isSpeaking -> OrbState.SPEAKING
    isThinking -> OrbState.THINKING
    wakeDetected || wakeListening -> OrbState.AWAKE
    else -> OrbState.IDLE
}
