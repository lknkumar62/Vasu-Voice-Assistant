package com.vasu.assistant.core.ai

import org.json.JSONObject

/**
 * Single source of truth for reaching an AI provider.
 *
 * The base URL used to be a private const inside GeminiProvider, the model id a
 * literal in SecureKeyStore, the timeouts inline in the OkHttp builder, and there
 * was no retry or fallback policy anywhere. Nothing could answer "what are we
 * asking for, and where" in one place, so a single unavailable model bricked chat.
 */
data class AiProviderConfig(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val primaryModel: String,
    /**
     * Tried in order when the preferred model is unavailable, and only when
     * [allowFallback] is set, so a substitution is always a configured decision
     * rather than the client quietly answering as something else.
     */
    val fallbackModels: List<String> = emptyList(),
    val allowFallback: Boolean = true,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 60,
    val writeTimeoutSeconds: Long = 30,
    /** Attempts after the first. Spent on transient failures only. */
    val maxRetries: Int = 2,
    val retryBackoffMillis: Long = 500
) {
    /** Ordered candidates, before availability is taken into account. */
    fun candidatesFor(preferred: String): List<String> {
        val chain = LinkedHashSet<String>()
        preferred.trim().takeIf { it.isNotEmpty() }?.let { chain.add(it) }
        if (allowFallback) {
            chain.add(primaryModel)
            chain.addAll(fallbackModels)
        }
        return chain.toList()
    }

    companion object {
        val GEMINI = AiProviderConfig(
            providerId = "gemini",
            displayName = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            primaryModel = "gemini-2.0-flash",
            // Every id here is still checked against the provider's own catalogue,
            // so an entry a given key cannot use is dropped rather than guessed at.
            fallbackModels = listOf(
                "gemini-2.5-flash",
                "gemini-2.0-flash-001",
                "gemini-1.5-flash",
                "gemini-flash-latest"
            )
        )
    }
}

/** One entry from the provider's model catalogue. */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val supportsGenerateContent: Boolean
)

sealed class ModelCatalog {
    data class Available(val models: List<ModelInfo>) : ModelCatalog() {
        /** Ids that can actually answer a chat turn. */
        val chatModelIds: Set<String>
            get() = models.filter { it.supportsGenerateContent }.map { it.id }.toSet()
    }

    data class Unavailable(val kind: AiErrorKind, val message: String) : ModelCatalog()
}

/**
 * Which models to try, in order.
 *
 * [available] is what the key actually supports, read from the provider. Null
 * means we could not find out, in which case the configured chain is tried
 * unfiltered rather than refusing to speak. When the catalogue *is* known,
 * anything missing from it is dropped — including the user's own choice, which is
 * what turns a repeated 404 into one clear "pick another model" message.
 */
internal fun selectModelChain(
    preferred: String,
    config: AiProviderConfig,
    available: Set<String>? = null
): List<String> {
    val candidates = config.candidatesFor(preferred)
    return if (available == null) candidates else candidates.filter { it in available }
}

/** Transient failures only, and never beyond the configured budget. */
internal fun shouldRetry(kind: AiErrorKind, attempt: Int, config: AiProviderConfig): Boolean =
    kind.isTransient && attempt < config.maxRetries

/** Exponential backoff, capped so a retry chain cannot stall a spoken reply. */
internal fun retryDelayMillis(attempt: Int, config: AiProviderConfig): Long =
    config.retryBackoffMillis shl attempt.coerceIn(0, 4)

/**
 * Reads a Gemini ListModels body.
 *
 * Models arrive as "models/<id>" but the generateContent URL needs the bare id.
 * A model that does not list generateContent cannot answer a chat turn, so it is
 * kept and flagged rather than dropped, which lets Settings explain the
 * difference instead of silently shortening the list.
 */
internal fun parseModelCatalog(body: String): List<ModelInfo> {
    val models = try {
        JSONObject(body).optJSONArray("models")
    } catch (e: Exception) {
        null
    } ?: return emptyList()

    return (0 until models.length()).mapNotNull { index ->
        val entry = models.optJSONObject(index) ?: return@mapNotNull null
        val id = entry.optString("name").removePrefix("models/").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val methods = entry.optJSONArray("supportedGenerationMethods")
        val supportsChat = (0 until (methods?.length() ?: 0)).any {
            methods?.optString(it) == "generateContent"
        }
        ModelInfo(id, entry.optString("displayName").ifBlank { id }, supportsChat)
    }
}
