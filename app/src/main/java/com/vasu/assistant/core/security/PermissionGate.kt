package com.vasu.assistant.core.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Risk levels for tools/commands
 */
enum class RiskLevel(val displayName: String, val requiredRole: UserRole) {
    LOW("Low", UserRole.GUEST),           // Weather, search, music
    MEDIUM("Medium", UserRole.FAMILY),     // Messages, calls, device state
    HIGH("High", UserRole.BOSS),           // Delete data, financial, security
    CRITICAL("Critical", UserRole.BOSS)    // Password changes, account actions
}

/**
 * Tool definition for permission checking
 */
data class Tool(
    val name: String,
    val description: String,
    val riskLevel: RiskLevel,
    val requiredRole: UserRole = riskLevel.requiredRole
)

/**
 * Permission check result
 */
sealed class PermissionResult {
    data object Granted : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
    data class RequiresConfirmation(val reason: String) : PermissionResult()
}

/**
 * PermissionGate - Checks permissions for tools/commands based on user roles.
 *
 * Every tool execution goes through PermissionGate:
 * 1. Check if Voice Guardian is enabled
 * 2. Check current speaker's role
 * 3. Check if role has permission for tool's risk level
 * 4. Require confirmation for high-risk actions
 */
@Singleton
class PermissionGate @Inject constructor(
    private val roleManager: RoleManager,
    private val settings: com.vasu.assistant.core.settings.VasuSettings
) {
    /**
     * Check if current speaker can execute a tool
     */
    fun checkPermission(tool: Tool): PermissionResult {
        // If auto-allow is enabled or guardian is disabled, grant immediately
        if (settings.autoAllowEnabled.value || !roleManager.guardianEnabled.value) {
            return PermissionResult.Granted
        }

        val currentRole = roleManager.getCurrentRole()

        // Check if user is blocked
        if (currentRole == UserRole.BLOCKED) {
            return PermissionResult.Denied("You are blocked from using VASU")
        }

        // Check if user is unknown
        if (currentRole == UserRole.UNKNOWN) {
            return PermissionResult.Denied("Voice not recognized. Please enroll first.")
        }

        // Check role priority
        if (currentRole.priority < tool.requiredRole.priority) {
            return PermissionResult.Denied(
                "Insufficient permissions. Required: ${tool.requiredRole.displayName}, " +
                        "Your role: ${currentRole.displayName}"
            )
        }

        // Require confirmation for high-risk actions if auto-allow is off
        if (tool.riskLevel == RiskLevel.HIGH || tool.riskLevel == RiskLevel.CRITICAL) {
            return PermissionResult.RequiresConfirmation(
                "This action requires confirmation: ${tool.description}"
            )
        }

        return PermissionResult.Granted
    }

    /**
     * Check if current speaker can execute a command by risk level
     */
    fun checkPermission(riskLevel: RiskLevel): PermissionResult {
        val tool = Tool(
            name = "command",
            description = "Execute command",
            riskLevel = riskLevel
        )
        return checkPermission(tool)
    }

    /**
     * Get current speaker info
     */
    fun getCurrentSpeakerInfo(): String {
        if (!roleManager.guardianEnabled.value) {
            return "Guardian disabled - All access"
        }

        val speaker = roleManager.currentSpeaker.value
        return if (speaker != null) {
            "${speaker.name} (${speaker.role.displayName})"
        } else {
            "No speaker verified"
        }
    }
}
