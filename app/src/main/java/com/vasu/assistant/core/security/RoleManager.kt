package com.vasu.assistant.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User roles with different permission levels
 */
enum class UserRole(val displayName: String, val priority: Int) {
    BOSS("Boss", 5),        // Unrestricted access
    FAMILY("Family", 4),    // Normal assistant functions
    FRIEND("Friend", 3),    // Informational only
    GUEST("Guest", 2),      // Conversation only
    BLOCKED("Blocked", 1),  // Deny all commands
    UNKNOWN("Unknown", 0)   // Not enrolled
}

/**
 * Enrolled voice profile
 */
data class EnrolledVoice(
    val id: String,
    val name: String,
    val role: UserRole,
    val embedding: FloatArray,
    val enrolledAt: Long = System.currentTimeMillis(),
    val lastVerified: Long = 0L,
    val verificationCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EnrolledVoice
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * RoleManager - Manages user roles and enrolled voices.
 *
 * Roles:
 * - BOSS: Unrestricted commands, voice enrollment, role management
 * - FAMILY: Normal assistant, restricted admin
 * - FRIEND: Informational commands only
 * - GUEST: Conversation only
 * - BLOCKED: Deny all commands
 */
@Singleton
class RoleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _enrolledVoices = MutableStateFlow<List<EnrolledVoice>>(emptyList())
    val enrolledVoices: StateFlow<List<EnrolledVoice>> = _enrolledVoices.asStateFlow()

    private val _currentSpeaker = MutableStateFlow<EnrolledVoice?>(null)
    val currentSpeaker: StateFlow<EnrolledVoice?> = _currentSpeaker.asStateFlow()

    private val _guardianEnabled = MutableStateFlow(false)
    val guardianEnabled: StateFlow<Boolean> = _guardianEnabled.asStateFlow()

    init {
        loadEnrolledVoices()
    }

    /**
     * Enable/disable Voice Guardian
     */
    fun setGuardianEnabled(enabled: Boolean) {
        _guardianEnabled.value = enabled
    }

    /**
     * Enroll a new voice
     */
    fun enrollVoice(
        name: String,
        role: UserRole,
        embedding: FloatArray
    ): EnrolledVoice {
        val voice = EnrolledVoice(
            id = generateId(),
            name = name,
            role = role,
            embedding = embedding
        )

        _enrolledVoices.value = _enrolledVoices.value + voice
        saveEnrolledVoices()
        return voice
    }

    /**
     * Remove an enrolled voice
     */
    fun removeVoice(id: String): Boolean {
        val voice = _enrolledVoices.value.find { it.id == id } ?: return false
        _enrolledVoices.value = _enrolledVoices.value.filter { it.id != id }
        saveEnrolledVoices()
        return true
    }

    /**
     * Update voice role
     */
    fun updateVoiceRole(id: String, newRole: UserRole): Boolean {
        val voices = _enrolledVoices.value.toMutableList()
        val index = voices.indexOfFirst { it.id == id }
        if (index == -1) return false

        voices[index] = voices[index].copy(role = newRole)
        _enrolledVoices.value = voices
        saveEnrolledVoices()
        return true
    }

    /**
     * Set current speaker (after verification)
     */
    fun setCurrentSpeaker(voice: EnrolledVoice?) {
        _currentSpeaker.value = voice
    }

    /**
     * Get current speaker's role
     */
    fun getCurrentRole(): UserRole {
        return _currentSpeaker.value?.role ?: UserRole.UNKNOWN
    }

    /**
     * Check if current speaker has required role
     */
    fun hasPermission(requiredRole: UserRole): Boolean {
        if (!_guardianEnabled.value) return true  // Guardian disabled = allow all
        return getCurrentRole().priority >= requiredRole.priority
    }

    /**
     * List all enrolled voices
     */
    fun listVoices(): List<EnrolledVoice> = _enrolledVoices.value

    /**
     * Get voice by ID
     */
    fun getVoice(id: String): EnrolledVoice? = _enrolledVoices.value.find { it.id == id }

    /**
     * Get voices by role
     */
    fun getVoicesByRole(role: UserRole): List<EnrolledVoice> {
        return _enrolledVoices.value.filter { it.role == role }
    }

    /**
     * Update verification count
     */
    fun recordVerification(id: String) {
        val voices = _enrolledVoices.value.toMutableList()
        val index = voices.indexOfFirst { it.id == id }
        if (index != -1) {
            voices[index] = voices[index].copy(
                lastVerified = System.currentTimeMillis(),
                verificationCount = voices[index].verificationCount + 1
            )
            _enrolledVoices.value = voices
            saveEnrolledVoices()
        }
    }

    private fun generateId(): String {
        return "voice_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun loadEnrolledVoices() {
        // In Phase 7, this will load from Room database
        // For now, start with empty list
        _enrolledVoices.value = emptyList()
    }

    private fun saveEnrolledVoices() {
        // In Phase 7, this will save to Room database
        // For now, just keep in memory
    }
}
