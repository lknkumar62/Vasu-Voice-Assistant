package com.vasu.assistant.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why an AI call failed. Callers need to distinguish "you have not set a key"
 * from "the network is down" from "Google rejected the key", because each one
 * needs a different spoken response and a different Settings hint.
 */
enum class AiErrorKind {
    NOT_CONFIGURED,
    INVALID_KEY,
    PERMISSION_DENIED,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    MODEL_NOT_FOUND,
    OFFLINE,
    TIMEOUT,
    SERVER_ERROR,
    BLOCKED_BY_SAFETY,
    MALFORMED_RESPONSE,
    UNKNOWN;

    /** Whether retrying the same request could plausibly succeed. */
    val isTransient: Boolean
        get() = this == RATE_LIMITED || this == OFFLINE || this == TIMEOUT || this == SERVER_ERROR
}

/**
 * Maps a ToolParameter type string onto a Gemini OpenAPI schema type. Top-level and
 * internal so the mapping can be unit tested without constructing the provider,
 * which needs Keystore access.
 */
internal fun toGeminiSchemaType(type: String): String = when (type.lowercase()) {
    "int", "integer", "long" -> "INTEGER"
    "float", "double", "number" -> "NUMBER"
    "bool", "boolean" -> "BOOLEAN"
    "array", "list" -> "ARRAY"
    "object", "map" -> "OBJECT"
    else -> "STRING"
}

sealed class AiResult {
    data class Text(
        val content: String,
        val tokensUsed: Int = 0
    ) : AiResult()

    data class FunctionCall(
        val name: String,
        val args: Map<String, Any>,
        val tokensUsed: Int = 0
    ) : AiResult()

    data class Failure(
        val kind: AiErrorKind,
        val message: String,
        val httpCode: Int? = null
    ) : AiResult()
}

/**
 * Real Google Gemini client.
 *
 * Uses org.json rather than Gson for request/response handling: it is part of the
 * Android platform and needs no reflection, so R8 in the minified release build
 * cannot strip model classes out from under it.
 *
 * The key is passed in the x-goog-api-key header, never as a query parameter, so
 * it cannot leak into URLs, proxy logs, or crash reports.
 */
@Singleton
class GeminiProvider @Inject constructor(
    private val keyStore: SecureKeyStore
) {
    private val config = AiProviderConfig.GEMINI

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val isConfigured: Boolean get() = keyStore.hasGeminiKey()

    /** The configured provider with the user's fallback preference applied. */
    private fun effectiveConfig(): AiProviderConfig =
        config.copy(allowFallback = keyStore.allowModelFallback)

    /**
     * Verifies the stored key by reading the model catalogue.
     *
     * This used to GET the single configured model, which proved nothing useful:
     * a 404 looked identical whether the key was wrong or the model simply was not
     * enabled for it. Listing tells us both, and names what the key can use.
     */
    suspend fun testConnection(): AiResult = withContext(Dispatchers.IO) {
        val key = keyStore.getGeminiKey() ?: return@withContext record(notConfigured())

        when (val catalog = fetchCatalog(key)) {
            is ModelCatalog.Unavailable ->
                record(AiResult.Failure(catalog.kind, catalog.message))
            is ModelCatalog.Available -> {
                val usable = catalog.chatModelIds
                keyStore.discoveredModels = usable
                val chain = selectModelChain(
                    keyStore.geminiModel,
                    effectiveConfig(),
                    usable.takeIf { it.isNotEmpty() }
                )
                if (chain.isEmpty()) {
                    record(noUsableModel(usable))
                } else {
                    record(
                        AiResult.Text(
                            "Connected. Will use ${chain.first()}; " +
                                "${usable.size} chat models available for this key."
                        )
                    )
                }
            }
        }
    }

    /**
     * Refreshes the cached catalogue so the model picker offers real choices
     * instead of a hardcoded guess.
     */
    suspend fun refreshModels(): ModelCatalog = withContext(Dispatchers.IO) {
        val key = keyStore.getGeminiKey()
            ?: return@withContext ModelCatalog.Unavailable(
                AiErrorKind.NOT_CONFIGURED,
                "No Gemini API key saved. Add one in Settings > AI Provider."
            )

        fetchCatalog(key).also {
            if (it is ModelCatalog.Available) keyStore.discoveredModels = it.chatModelIds
        }
    }

    /**
     * Sends a turn to Gemini. When [tools] is non-empty the model may answer with
     * a function call instead of text; tool execution stays on our side so the
     * model can never invoke anything that is not in the registry.
     *
     * The request is tried against each model in the resolved chain. Only an
     * unavailable model advances the chain — a rejected key or a safety block
     * fails identically on every model, so retrying those just multiplies the wait.
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String,
        history: List<ChatMessage> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): AiResult = withContext(Dispatchers.IO) {
        val key = keyStore.getGeminiKey() ?: return@withContext record(notConfigured())

        val settings = effectiveConfig()
        val catalog = knownModels(key)
        val chain = selectModelChain(keyStore.geminiModel, settings, catalog)
        if (chain.isEmpty()) return@withContext record(noUsableModel(catalog))

        val payload = buildPayload(prompt, systemPrompt, history, tools, temperature, maxTokens)
            .toString()

        for (model in chain) {
            val request = Request.Builder()
                .url("${settings.baseUrl}/models/$model:generateContent")
                .addHeader("x-goog-api-key", key)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON))
                .build()

            when (val outcome = sendWithRetry(request, model)) {
                is HttpOutcome.Body -> {
                    val parsed = parseGeminiCandidate(outcome.text)
                    if (parsed !is AiResult.Failure) keyStore.activeModel = model
                    return@withContext record(parsed)
                }
                is HttpOutcome.Failed -> {
                    if (outcome.failure.kind != AiErrorKind.MODEL_NOT_FOUND) {
                        return@withContext record(outcome.failure)
                    }
                    // The catalogue said this model was usable and it was not, so it
                    // is stale; drop it and let the next turn read a fresh one.
                    Log.w(TAG, "Model $model unavailable for this key, trying next candidate")
                    keyStore.discoveredModels = emptySet()
                }
            }
        }

        record(noUsableModel(catalog))
    }

    /**
     * The models this key may use, cached after the first lookup.
     *
     * Null when the catalogue could not be read, which leaves the configured chain
     * unfiltered rather than refusing to try: being unable to ask is not the same
     * as having no model.
     */
    private fun knownModels(key: String): Set<String>? {
        keyStore.discoveredModels.takeIf { it.isNotEmpty() }?.let { return it }

        return when (val catalog = fetchCatalog(key)) {
            is ModelCatalog.Available -> catalog.chatModelIds
                .also { keyStore.discoveredModels = it }
                .takeIf { it.isNotEmpty() }
            is ModelCatalog.Unavailable -> {
                Log.w(TAG, "Could not read the model catalogue: ${catalog.kind}")
                null
            }
        }
    }

    private fun fetchCatalog(key: String): ModelCatalog {
        val request = Request.Builder()
            .url("${config.baseUrl}/models?pageSize=$CATALOG_PAGE_SIZE")
            .addHeader("x-goog-api-key", key)
            .get()
            .build()

        return when (val outcome = send(request)) {
            is HttpOutcome.Body -> ModelCatalog.Available(parseModelCatalog(outcome.text))
            is HttpOutcome.Failed ->
                ModelCatalog.Unavailable(outcome.failure.kind, outcome.failure.message)
        }
    }

    private fun notConfigured() = AiResult.Failure(
        AiErrorKind.NOT_CONFIGURED,
        "No Gemini API key saved. Add one in Settings > AI Provider."
    )

    /**
     * The clean end of the road: the key works but cannot serve anything we are
     * configured to ask for. Names what it *can* use so Settings is actionable.
     */
    private fun noUsableModel(available: Set<String>?): AiResult.Failure {
        val chosen = keyStore.geminiModel
        val detail = if (available.isNullOrEmpty()) {
            "Model '$chosen' is not available for this API key, and the list of " +
                "models the key can use could not be read."
        } else {
            "Model '$chosen' is not available for this API key. It can use: " +
                available.sorted().take(CATALOG_NAMES_SHOWN).joinToString(", ") +
                ". Choose one in Settings > AI Provider."
        }
        return AiResult.Failure(AiErrorKind.MODEL_NOT_FOUND, detail)
    }


    private fun buildPayload(
        prompt: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        tools: List<ToolDefinition>,
        temperature: Float,
        maxTokens: Int
    ): JSONObject {
        val contents = JSONArray()
        history.forEach { message ->
            // Gemini only accepts "user" and "model" roles; system text goes in
            // systemInstruction, so anything not from the user is mapped to model.
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        )

        val payload = JSONObject()
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", temperature)
                    .put("maxOutputTokens", maxTokens)
            )

        if (systemPrompt.isNotBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
        }

        if (tools.isNotEmpty()) {
            val declarations = JSONArray()
            tools.forEach { declarations.put(toFunctionDeclaration(it)) }
            payload.put(
                "tools",
                JSONArray().put(JSONObject().put("functionDeclarations", declarations))
            )
        }

        return payload
    }

    private fun toFunctionDeclaration(tool: ToolDefinition): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()

        tool.parameters.forEach { param ->
            properties.put(
                param.name,
                JSONObject()
                    .put("type", param.type.toSchemaType())
                    .put("description", param.description)
            )
            if (param.required) required.put(param.name)
        }

        val declaration = JSONObject()
            .put("name", tool.name)
            .put("description", "${tool.description} (risk: ${tool.riskLevel})")

        // Gemini rejects an OBJECT schema with no properties, so parameterless
        // tools must omit the parameters field entirely.
        if (tool.parameters.isNotEmpty()) {
            declaration.put(
                "parameters",
                JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", properties)
                    .put("required", required)
            )
        }
        return declaration
    }

    private fun String.toSchemaType(): String = toGeminiSchemaType(this)

    /**
     * Runs the request, translating transport and HTTP failures into typed results.
     * Returns the raw body on success so the caller decides how to read it — chat
     * turns and the model catalogue parse the same transport very differently.
     */
    private fun send(request: Request, model: String? = null): HttpOutcome = try {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                HttpOutcome.Body(body)
            } else {
                HttpOutcome.Failed(mapGeminiHttpError(response.code, body, model))
            }
        }
    } catch (e: UnknownHostException) {
        HttpOutcome.Failed(AiResult.Failure(AiErrorKind.OFFLINE, "No internet connection"))
    } catch (e: SocketTimeoutException) {
        HttpOutcome.Failed(AiResult.Failure(AiErrorKind.TIMEOUT, "Gemini took too long to respond"))
    } catch (e: IOException) {
        HttpOutcome.Failed(
            AiResult.Failure(AiErrorKind.OFFLINE, "Network error: ${e.message ?: "connection failed"}")
        )
    } catch (e: Exception) {
        // The exception text is for the log. It reaches a text-to-speech engine if
        // it is returned, and a stack frame read aloud tells the user nothing.
        Log.e(TAG, "Unexpected Gemini failure", e)
        HttpOutcome.Failed(AiResult.Failure(AiErrorKind.UNKNOWN, "Gemini request failed unexpectedly"))
    }

    /**
     * Retries transient failures only. A rejected key or a blocked prompt fails the
     * same way every time, so retrying it wastes the seconds the user is waiting.
     */
    private suspend fun sendWithRetry(request: Request, model: String? = null): HttpOutcome {
        var attempt = 0
        while (true) {
            val outcome = send(request, model)
            if (outcome !is HttpOutcome.Failed) return outcome
            if (!shouldRetry(outcome.failure.kind, attempt, config)) return outcome
            delay(retryDelayMillis(attempt, config))
            attempt++
        }
    }

    /** Keeps the Settings connection state in step with what actually happened. */
    private fun <T : AiResult> record(result: T): T {
        if (result is AiResult.Failure) {
            keyStore.lastError = "${result.kind}: ${result.message}"
        } else {
            keyStore.lastSuccessfulConnection = System.currentTimeMillis()
            keyStore.lastError = null
        }
        return result
    }

    companion object {
        private const val TAG = "GeminiProvider"
        private const val CATALOG_PAGE_SIZE = 100
        private const val CATALOG_NAMES_SHOWN = 6
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** A completed HTTP exchange, before anyone decides what the body means. */
private sealed class HttpOutcome {
    data class Body(val text: String) : HttpOutcome()
    data class Failed(val failure: AiResult.Failure) : HttpOutcome()
}

/**
 * Maps an HTTP failure onto the taxonomy. Top-level and internal so the mapping
 * can be unit tested without a provider instance, which needs Keystore access.
 *
 * [model] is the model this particular attempt asked for, not the user's stored
 * choice: while walking a fallback chain those differ, and naming the wrong one
 * sends the user to Settings to fix something that was never the problem.
 */
internal fun mapGeminiHttpError(code: Int, body: String, model: String?): AiResult.Failure {
    // Never echo the raw body wholesale; it can contain the echoed request.
    val apiMessage = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (e: Exception) {
        ""
    }

    val kind = when (code) {
        400 -> if (apiMessage.contains("API key", ignoreCase = true)) {
            AiErrorKind.INVALID_KEY
        } else {
            AiErrorKind.UNKNOWN
        }
        401 -> AiErrorKind.INVALID_KEY
        403 -> if (apiMessage.contains("quota", ignoreCase = true)) {
            AiErrorKind.QUOTA_EXCEEDED
        } else {
            AiErrorKind.PERMISSION_DENIED
        }
        404 -> AiErrorKind.MODEL_NOT_FOUND
        429 -> AiErrorKind.RATE_LIMITED
        in 500..599 -> AiErrorKind.SERVER_ERROR
        else -> AiErrorKind.UNKNOWN
    }

    val friendly = when (kind) {
        AiErrorKind.INVALID_KEY -> "Gemini rejected the API key. Check it in Settings."
        AiErrorKind.PERMISSION_DENIED ->
            "Gemini denied access. Enable the Generative Language API for this key."
        AiErrorKind.QUOTA_EXCEEDED -> "Gemini quota exhausted for this key."
        AiErrorKind.MODEL_NOT_FOUND ->
            "Model '${model ?: "requested"}' is not available for this API key."
        AiErrorKind.RATE_LIMITED -> "Too many requests. Try again in a moment."
        AiErrorKind.SERVER_ERROR -> "Gemini is having problems (HTTP $code)."
        else -> apiMessage.ifBlank { "Gemini request failed (HTTP $code)." }
    }

    return AiResult.Failure(kind, friendly, code)
}

/**
 * Reads a generateContent response. Top-level and internal so the parsing can be
 * unit tested against real Gemini bodies without constructing the provider.
 */
internal fun parseGeminiCandidate(body: String): AiResult {
    val root = try {
        JSONObject(body)
    } catch (e: Exception) {
        return AiResult.Failure(AiErrorKind.MALFORMED_RESPONSE, "Could not parse Gemini response")
    }

    val tokens = root.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0

    val promptFeedback = root.optJSONObject("promptFeedback")
    val blockReason = promptFeedback?.optString("blockReason", "")?.takeIf { it.isNotBlank() }
    if (blockReason != null) {
        return AiResult.Failure(
            AiErrorKind.BLOCKED_BY_SAFETY,
            "Gemini blocked this request ($blockReason)"
        )
    }

    val candidates = root.optJSONArray("candidates")
    if (candidates == null || candidates.length() == 0) {
        return AiResult.Failure(AiErrorKind.MALFORMED_RESPONSE, "Gemini returned no candidates")
    }

    val candidate = candidates.getJSONObject(0)
    val finishReason = candidate.optString("finishReason", "")
    if (finishReason == "SAFETY" || finishReason == "PROHIBITED_CONTENT") {
        return AiResult.Failure(
            AiErrorKind.BLOCKED_BY_SAFETY,
            "Gemini stopped this response for safety reasons"
        )
    }

    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        ?: return AiResult.Failure(AiErrorKind.MALFORMED_RESPONSE, "Gemini response had no content")

    // A function call takes priority: if the model chose a tool, that is the
    // actionable result even when it also emitted commentary text.
    for (i in 0 until parts.length()) {
        val call = parts.getJSONObject(i).optJSONObject("functionCall") ?: continue
        val name = call.optString("name").takeIf { it.isNotBlank() } ?: continue
        return AiResult.FunctionCall(name, call.optJSONObject("args").toArgMap(), tokens)
    }

    val text = buildString {
        for (i in 0 until parts.length()) {
            parts.getJSONObject(i).optString("text").takeIf { it.isNotBlank() }?.let { append(it) }
        }
    }

    return if (text.isBlank()) {
        AiResult.Failure(AiErrorKind.MALFORMED_RESPONSE, "Gemini returned an empty response")
    } else {
        AiResult.Text(text.trim(), tokens)
    }
}

private fun JSONObject?.toArgMap(): Map<String, Any> {
    if (this == null) return emptyMap()
    return keys().asSequence().mapNotNull { key ->
        when (val value = opt(key)) {
            null, JSONObject.NULL -> null
            is JSONArray -> key to (0 until value.length()).mapNotNull { value.opt(it) }
            else -> key to value
        }
    }.toMap()
}
