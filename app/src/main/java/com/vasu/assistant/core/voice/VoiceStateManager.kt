package com.vasu.assistant.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative state manager for the voice system.
 * Acts as the single source of truth for STT, TTS, and WakeWord components.
 */
@Singleton
class VoiceStateManager @Inject constructor() {
    private companion object {
        const val TAG = "VoiceStateManager"
    }

    private val _state = MutableStateFlow(GeminiVoiceState.IDLE)
    val state: StateFlow<GeminiVoiceState> = _state.asStateFlow()

    private val mutex = Mutex()

    /**
     * Transitions the system to a new state if the transition is valid.
     * @param newState The state to transition to.
     * @return True if transition was successful, false otherwise.
     */
    suspend fun transitionTo(newState: GeminiVoiceState): Boolean = mutex.withLock {
        val currentState = _state.value
        if (currentState == newState) return true

        if (isValidTransition(currentState, newState)) {
            Log.d(TAG, "VoiceState Transition: $currentState -> $newState")
            _state.value = newState
            return true
        } else {
            Log.w(TAG, "Invalid VoiceState Transition Attempted: $currentState -> $newState")
            return false
        }
    }

    private fun isValidTransition(from: GeminiVoiceState, to: GeminiVoiceState): Boolean {
        // Any state can transition to STOPPING or ERROR
        if (to == GeminiVoiceState.STOPPING || to == GeminiVoiceState.ERROR) {
            return true
        }

        // STOPPING and ERROR always transition back to IDLE
        if (from == GeminiVoiceState.STOPPING || from == GeminiVoiceState.ERROR) {
            return to == GeminiVoiceState.IDLE
        }

        return when (from) {
            GeminiVoiceState.IDLE -> {
                // IDLE -> COMMAND_LISTENING (manual trigger)
                to == GeminiVoiceState.COMMAND_LISTENING
            }
            GeminiVoiceState.BACKGROUND_LISTENING -> {
                // BACKGROUND_LISTENING -> WAKE_DETECTED
                to == GeminiVoiceState.WAKE_DETECTED
            }
            GeminiVoiceState.WAKE_DETECTED -> {
                // WAKE_DETECTED -> COMMAND_LISTENING
                to == GeminiVoiceState.COMMAND_LISTENING
            }
            GeminiVoiceState.COMMAND_LISTENING -> {
                // COMMAND_LISTENING -> PROCESSING
                to == GeminiVoiceState.PROCESSING
            }
            GeminiVoiceState.PROCESSING -> {
                // PROCESSING -> SPEAKING
                to == GeminiVoiceState.SPEAKING
            }
            GeminiVoiceState.SPEAKING -> {
                // SPEAKING -> BACKGROUND_LISTENING or IDLE
                to == GeminiVoiceState.BACKGROUND_LISTENING || to == GeminiVoiceState.IDLE
            }
            else -> false
        }
    }

    fun isListening(): Boolean {
        return state.value == GeminiVoiceState.BACKGROUND_LISTENING || 
               state.value == GeminiVoiceState.COMMAND_LISTENING
    }

    fun isSpeaking(): Boolean {
        return state.value == GeminiVoiceState.SPEAKING
    }

    fun isProcessing(): Boolean {
        return state.value == GeminiVoiceState.PROCESSING
    }
}
