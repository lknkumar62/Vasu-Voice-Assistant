package com.vasu.assistant.avatar

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VasuAvatar @Inject constructor(
    private val avatarManager: AvatarManager
) {
    private val motionMap = mapOf(
        "idle" to "idle_motion",
        "listening" to "listening_motion",
        "thinking" to "thinking_motion",
        "speaking" to "speaking_motion",
        "happy" to "happy_motion",
        "sad" to "sad_motion",
        "angry" to "angry_motion",
        "surprised" to "surprised_motion",
        "error" to "error_motion"
    )

    fun getStateMotion(): String {
        val state = avatarManager.getCurrentState().name.lowercase()
        return motionMap[state] ?: "idle_motion"
    }

    fun setSpeaking() { avatarManager.onSpeaking() }
    fun setIdle() { avatarManager.onIdle() }
    fun setListening() { avatarManager.onListening() }
    fun setThinking() { avatarManager.onThinking() }
    fun setHappy() { avatarManager.onHappy() }
    fun setSad() { avatarManager.onSad() }
    fun setAngry() { avatarManager.onAngry() }
    fun setSurprised() { avatarManager.onSurprised() }
    fun setError() { avatarManager.onError() }
}
