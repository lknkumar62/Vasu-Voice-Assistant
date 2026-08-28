package com.vasu.assistant.avatar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentState: AvatarState = AvatarState.IDLE
    private var enabled: Boolean = false

    enum class AvatarState { IDLE, LISTENING, THINKING, SPEAKING, HAPPY, SAD, ANGRY, SURPRISED, ERROR }

    fun isEnabled() = enabled
    fun setEnabled(value: Boolean) { enabled = value }
    fun getCurrentState() = currentState

    fun setState(state: AvatarState): Boolean {
        if (!enabled) return false
        currentState = state
        return true
    }

    fun onListening() = setState(AvatarState.LISTENING)
    fun onThinking() = setState(AvatarState.THINKING)
    fun onSpeaking() = setState(AvatarState.SPEAKING)
    fun onIdle() = setState(AvatarState.IDLE)
    fun onHappy() = setState(AvatarState.HAPPY)
    fun onSad() = setState(AvatarState.SAD)
    fun onAngry() = setState(AvatarState.ANGRY)
    fun onSurprised() = setState(AvatarState.SURPRISED)
    fun onError() = setState(AvatarState.ERROR)

    fun getAvailableStates(): List<String> = AvatarState.values().map { it.name }
}
