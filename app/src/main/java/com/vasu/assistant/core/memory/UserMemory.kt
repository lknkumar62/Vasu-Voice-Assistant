package com.vasu.assistant.core.memory

import com.vasu.assistant.database.UserMemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserMemory - Manages long-term user preferences and facts.
 *
 * Stores and retrieves user information like:
 * - Name, preferences, habits
 * - Frequently used apps
 * - Important dates
 * - Custom settings
 *
 * Sensitive info (passwords, OTPs) is NOT stored.
 */
@Singleton
class UserMemory @Inject constructor(
    private val repository: MemoryRepository
) {
    // Sensitive patterns to avoid storing
    private val sensitivePatterns = listOf(
        Regex("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\b"),  // Credit card
        Regex("\\b\\d{6}\\b"),  // OTP
        Regex("(?i)(password|passwd|pwd)\\s*[=:]\\s*\\S+"),
        Regex("(?i)(secret|token|key)\\s*[=:]\\s*\\S+")
    )

    /**
     * Remember a fact about the user
     */
    suspend fun remember(key: String, value: String): Boolean {
        if (isSensitive(value)) return false
        repository.remember(key, value, "explicit")
        return true
    }

    /**
     * Recall a fact
     */
    suspend fun recall(key: String): String? {
        return repository.recall(key)
    }

    /**
     * Learn from conversation (auto-extract facts)
     */
    suspend fun learnFromConversation(text: String) {
        val lowerText = text.lowercase()

        // Extract name
        val namePatterns = listOf(
            Regex("(?:my name is|i'm|mera naam|main)\\s+([A-Za-z]+)"),
            Regex("(?:call me|bulaao)\\s+([A-Za-z]+)")
        )
        for (pattern in namePatterns) {
            val match = pattern.find(lowerText)
            if (match != null) {
                val name = match.groupValues[1].replaceFirstChar { it.uppercase() }
                remember("user_name", name)
            }
        }

        // Extract preferences
        val prefPatterns = mapOf(
            Regex("(?:i (?:like|love|prefer)|mujhe pasand)\\s+(.+)") to "preference",
            Regex("(?:my favorite|mera favorite)\\s+(?:is|hai)\\s+(.+)") to "favorite"
        )
        for ((pattern, type) in prefPatterns) {
            val match = pattern.find(lowerText)
            if (match != null) {
                val value = match.groupValues[1].trim()
                if (!isSensitive(value)) {
                    remember("${type}_${System.currentTimeMillis()}", value)
                }
            }
        }
    }

    /**
     * Search memory
     */
    suspend fun search(query: String): List<UserMemoryEntity> {
        return repository.searchMemory(query)
    }

    /**
     * Forget a fact
     */
    suspend fun forget(key: String) {
        repository.forget(key)
    }

    /**
     * Get all memories as context
     */
    suspend fun getMemoryContext(): String {
        val memories = repository.getAllMemory()
        if (memories.isEmpty()) return ""

        return buildString {
            append("Things I remember about the user:\n")
            memories.take(20).forEach { memory ->
                append("- ${memory.key}: ${memory.value}\n")
            }
        }
    }

    /**
     * Get memory count
     */
    suspend fun getCount(): Int {
        return repository.getMemoryCount()
    }

    /**
     * Check if value is sensitive
     */
    private fun isSensitive(value: String): Boolean {
        return sensitivePatterns.any { it.containsMatchIn(value) }
    }
}
