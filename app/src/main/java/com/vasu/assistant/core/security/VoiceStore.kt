package com.vasu.assistant.core.security

/**
 * One persisted voice enrollment. Pure Kotlin (no Android types) so the
 * Guardian persistence contract is unit-testable on the JVM.
 */
data class StoredVoice(
    val id: String,
    val name: String,
    val roleName: String,
    val embedding: FloatArray,
    val enrolledAt: Long,
    val lastVerified: Long,
    val verificationCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredVoice) return false
        return id == other.id &&
            name == other.name &&
            roleName == other.roleName &&
            embedding.contentEquals(other.embedding) &&
            enrolledAt == other.enrolledAt &&
            lastVerified == other.lastVerified &&
            verificationCount == other.verificationCount
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + roleName.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + enrolledAt.hashCode()
        result = 31 * result + lastVerified.hashCode()
        result = 31 * result + verificationCount
        return result
    }
}

/** Full snapshot of what must survive a process restart. */
data class VoiceStoreSnapshot(
    val voices: List<StoredVoice> = emptyList(),
    val guardianEnabled: Boolean = false
)

/**
 * Storage backend for Voice Guardian enrollments.
 *
 * RoleManager reads this once on init and writes on every mutation.
 * Implementations must swallow their own storage failures on [load] and
 * return an empty snapshot rather than crash the app at startup.
 */
interface VoiceStore {
    fun load(): VoiceStoreSnapshot
    fun save(snapshot: VoiceStoreSnapshot)
}
