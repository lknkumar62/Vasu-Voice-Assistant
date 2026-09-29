package com.vasu.assistant.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    /**
     * Full-field equality is load-bearing: StateFlow deduplicates on [equals],
     * so id-only equality used to silently swallow role/verification updates.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnrolledVoice) return false
        return id == other.id &&
            name == other.name &&
            role == other.role &&
            embedding.contentEquals(other.embedding) &&
            enrolledAt == other.enrolledAt &&
            lastVerified == other.lastVerified &&
            verificationCount == other.verificationCount
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + enrolledAt.hashCode()
        result = 31 * result + lastVerified.hashCode()
        result = 31 * result + verificationCount
        return result
    }
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
    private val voiceStore: VoiceStore
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
     * Enable/disable Voice Guardian (persisted — survives restart)
     */
    fun setGuardianEnabled(enabled: Boolean) {
        if (_guardianEnabled.value == enabled) return
        _guardianEnabled.value = enabled
        saveEnrolledVoices()
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

        _enrolledVoices.update { it + voice }
        saveEnrolledVoices()
        return voice
    }

    /**
     * Remove an enrolled voice
     */
    fun removeVoice(id: String): Boolean {
        if (_enrolledVoices.value.none { it.id == id }) return false
        _enrolledVoices.update { list -> list.filter { it.id != id } }
        saveEnrolledVoices()
        return true
    }

    /**
     * Update voice role
     */
    fun updateVoiceRole(id: String, newRole: UserRole): Boolean {
        if (_enrolledVoices.value.none { it.id == id }) return false
        _enrolledVoices.update { list ->
            list.map { if (it.id == id) it.copy(role = newRole) else it }
        }
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
        if (_enrolledVoices.value.none { it.id == id }) return
        _enrolledVoices.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        lastVerified = System.currentTimeMillis(),
                        verificationCount = it.verificationCount + 1
                    )
                } else it
            }
        }
        saveEnrolledVoices()
    }

    private fun generateId(): String {
        return "voice_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /** Reads persisted enrollments + guardian toggle (survives app restart). */
    private fun loadEnrolledVoices() {
        val snapshot = voiceStore.load()
        _enrolledVoices.value = snapshot.voices.map { stored ->
            EnrolledVoice(
                id = stored.id,
                name = stored.name,
                role = UserRole.entries.firstOrNull { it.name == stored.roleName } ?: UserRole.UNKNOWN,
                embedding = stored.embedding,
                enrolledAt = stored.enrolledAt,
                lastVerified = stored.lastVerified,
                verificationCount = stored.verificationCount
            )
        }
        _guardianEnabled.value = snapshot.guardianEnabled
    }

    /** Writes the full Guardian state after every mutation. */
    private fun saveEnrolledVoices() {
        voiceStore.save(
            VoiceStoreSnapshot(
                voices = _enrolledVoices.value.map { voice ->
                    StoredVoice(
                        id = voice.id,
                        name = voice.name,
                        roleName = voice.role.name,
                        embedding = voice.embedding,
                        enrolledAt = voice.enrolledAt,
                        lastVerified = voice.lastVerified,
                        verificationCount = voice.verificationCount
                    )
                },
                guardianEnabled = _guardianEnabled.value
            )
        )
    }
}
