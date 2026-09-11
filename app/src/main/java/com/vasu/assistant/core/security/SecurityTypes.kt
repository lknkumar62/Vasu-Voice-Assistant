package com.vasu.assistant.core.security

enum class RiskLevel(val displayName: String, val requiredRole: UserRole) {
    LOW("Low", UserRole.GUEST),
    MEDIUM("Medium", UserRole.FRIEND),
    HIGH("High", UserRole.FAMILY),
    CRITICAL("Critical", UserRole.BOSS)
}

sealed class VerificationResult {
    data class Verified(val speaker: EnrolledVoice) : VerificationResult()
    data class Unverified(val similarity: Float) : VerificationResult()
    data object NoEnrolledVoices : VerificationResult()
}

sealed class PermissionResult {
    data object Granted : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
}
