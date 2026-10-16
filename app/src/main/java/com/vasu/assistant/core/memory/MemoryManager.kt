package com.vasu.assistant.core.memory

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MemoryManager - Main coordinator for all memory systems.
 *
 * Coordinates:
 * - ConversationMemory (short-term context)
 * - UserMemory (long-term facts)
 *
 * Provides unified API for memory operations.
 */
@Singleton
class MemoryManager @Inject constructor(
    private val conversationMemory: ConversationMemory,
    private val userMemory: UserMemory
) {
    /** Explicit memory commands recognised by [detectCommand]. */
    enum class MemoryCommand { REMEMBER, FORGET }

    /**
     * Process user input and extract learnable information
     */
    suspend fun processInput(input: String) {
        // Learn from conversation
        userMemory.learnFromConversation(input)

        // Vault pre-extract guard (pure part only; LLM wiring lands later):
        // only fact-bearing input proceeds to extraction, saving LLM cost.
        // Synchronous-safe: stays in this suspend context, no new scope.
        try {
            if (VaultExtractor.looksFactBearing(input)) {
                // Deferred: once LLM wiring lands, the assistant-reply path
                // will call storeVaultFacts(input, assistantReply, llmJson).
                // No LLM call here by design.
            }
        } catch (_: Exception) {
            // Vault guard must never break input processing.
        }

        performCommand(input)
    }

    /**
     * Which explicit memory command [input] is, or null for a normal turn.
     * Pure, no side effects: the AI orchestrator uses this to short-circuit
     * remember/forget turns before any other matcher can hijack them.
     */
    fun detectCommand(input: String): MemoryCommand? {
        val lower = input.trim().lowercase(Locale.ROOT)
        return when {
            REMEMBER_PREFIXES.any { lower.startsWith(it) } -> MemoryCommand.REMEMBER
            FORGET_PREFIXES.any { lower.startsWith(it) } -> MemoryCommand.FORGET
            else -> null
        }
    }

    /**
     * Execute the explicit command in [input] (detected by [detectCommand]).
     * Returns true when a fact was actually stored or deleted.
     */
    suspend fun performCommand(input: String): Boolean {
        return when (detectCommand(input)) {
            MemoryCommand.REMEMBER -> {
                val fact = valueAfter(REMEMBER_PREFIXES, input)
                fact.isNotBlank() && userMemory.remember("fact_${System.currentTimeMillis()}", fact)
            }
            MemoryCommand.FORGET -> {
                val key = valueAfter(FORGET_PREFIXES, input)
                userMemory.forgetMatching(key)
            }
            null -> false
        }
    }

    /**
     * Store already-extracted vault facts (model JSON) into long-term memory.
     * Called by the future LLM response path, not by processInput directly,
     * since extraction requires an LLM call. Stores ONLY facts; entities,
     * relationships and commitments are ignored in this batch.
     * UserMemory.remember already drops sensitive values.
     */
    suspend fun storeVaultFacts(userMsg: String, assistantMsg: String, rawJson: String) {
        try {
            // Dedup reference for the future vault table; no column for it yet.
            VaultExtractor.sourceRef(userMsg, assistantMsg)
            val extraction = VaultExtractor.parseExtractionResponse(rawJson, userMsg)
            extraction.facts.forEachIndexed { index, fact ->
                userMemory.remember(
                    "fact_${System.currentTimeMillis()}_$index",
                    "${fact.subject} ${fact.predicate} ${fact.obj}"
                )
            }
        } catch (_: Exception) {
            // Vault storage must never break the caller.
        }
    }

    /**
     * Get full context for AI
     */
    suspend fun getFullContext(): String {
        val conversationContext = conversationMemory.getFormattedHistory(10)
        val userContext = userMemory.getMemoryContext()

        return buildString {
            if (userContext.isNotBlank()) {
                append(userContext)
                append("\n\n")
            }
            if (conversationContext.isNotBlank()) {
                append("Recent conversation:\n")
                append(conversationContext)
            }
        }
    }

    /**
     * Long-term user facts as prompt text; empty when nothing is remembered.
     * Preferred over [getFullContext] for prompt injection because the recent
     * conversation is already sent to the model as chat history.
     */
    suspend fun getUserFacts(): String {
        return userMemory.getMemoryContext()
    }

    /**
     * Add message to conversation
     */
    suspend fun addMessage(role: String, content: String) {
        conversationMemory.addMessage(role, content)
    }

    /**
     * Start new conversation
     */
    fun newConversation(): String {
        return conversationMemory.newConversation()
    }

    /**
     * Remember a fact
     */
    suspend fun remember(key: String, value: String): Boolean {
        return userMemory.remember(key, value)
    }

    /**
     * Recall a fact
     */
    suspend fun recall(key: String): String? {
        return userMemory.recall(key)
    }

    /**
     * Search memory
     */
    suspend fun search(query: String): String {
        val results = userMemory.search(query)
        return if (results.isEmpty()) {
            "I don't remember anything about that."
        } else {
            results.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }
    }

    /**
     * Clear current conversation
     */
    fun clearConversation() {
        conversationMemory.clearCurrent()
    }

    companion object {
        private val REMEMBER_PREFIXES = listOf(
            "remember that", "remember this",
            "yaad rakhna", "yaad rakho", "yaad rakh",
            "याद रखना", "याद रखो", "याद रखिए", "याद रख"
        )
        private val FORGET_PREFIXES = listOf(
            "forget that", "forget this",
            "bhool jao", "bhool ja",
            "भूल जाओ", "भूल जा"
        )

        /** Text after the longest matching command prefix, cleaned up. */
        private fun valueAfter(prefixes: List<String>, input: String): String {
            val lower = input.trim().lowercase(Locale.ROOT)
            val prefix = prefixes.filter { lower.startsWith(it) }.maxByOrNull { it.length }
                ?: return ""
            return lower.removePrefix(prefix).trim().trimStart(':', ',', '-', '—').trim()
        }
    }
}
