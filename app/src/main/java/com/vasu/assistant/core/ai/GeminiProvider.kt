package com.vasu.assistant.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
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
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val isConfigured: Boolean get() = keyStore.hasGeminiKey()

    /**
     * Verifies the stored key against the models endpoint. Cheapest call that
     * still proves the credential works, so it is safe to expose as a button.
     */
    suspend fun testConnection(): AiResult = withContext(Dispatchers.IO) {
        val key = keyStore.getGeminiKey()
            ?: return@withContext AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "No Gemini API key saved. Add one in Settings > AI Provider."
            )

        val model = keyStore.geminiModel
        val request = Request.Builder()
            .url("$BASE_URL/models/$model")
            .addHeader("x-goog-api-key", key)
            .get()
            .build()

        executeAndRecord(request) { body ->
            val name = JSONObject(body).optString("name", model)
            AiResult.Text("Connected to $name")
        }
    }

    /**
     * Sends a turn to Gemini. When [tools] is non-empty the model may answer with
     * a function call instead of text; tool execution stays on our side so the
     * model can never invoke anything that is not in the registry.
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String,
        history: List<ChatMessage> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): AiResult = withContext(Dispatchers.IO) {
        val key = keyStore.getGeminiKey()
            ?: return@withContext AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "No Gemini API key saved. Add one in Settings > AI Provider."
            )

        val model = keyStore.geminiModel
        val payload = buildPayload(prompt, systemPrompt, history, tools, temperature, maxTokens)

        val request = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .addHeader("x-goog-api-key", key)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        executeAndRecord(request) { body -> parseCandidate(body) }
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

    private fun parseCandidate(body: String): AiResult {
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

    /**
     * Runs the request, translates transport and HTTP failures into typed
     * results, and records connection state for the Settings screen.
     */
    private fun executeAndRecord(request: Request, onSuccess: (String) -> AiResult): AiResult {
        val result = try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    onSuccess(body)
                } else {
                    mapHttpError(response.code, body)
                }
            }
        } catch (e: UnknownHostException) {
            AiResult.Failure(AiErrorKind.OFFLINE, "No internet connection")
        } catch (e: SocketTimeoutException) {
            AiResult.Failure(AiErrorKind.TIMEOUT, "Gemini took too long to respond")
        } catch (e: IOException) {
            AiResult.Failure(AiErrorKind.OFFLINE, "Network error: ${e.message ?: "connection failed"}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Gemini failure: ${e.javaClass.simpleName}")
            AiResult.Failure(AiErrorKind.UNKNOWN, e.message ?: "Unknown error")
        }

        when (result) {
            is AiResult.Failure -> keyStore.lastError = "${result.kind}: ${result.message}"
            else -> {
                keyStore.lastSuccessfulConnection = System.currentTimeMillis()
                keyStore.lastError = null
            }
        }
        return result
    }

    private fun mapHttpError(code: Int, body: String): AiResult.Failure {
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
            AiErrorKind.PERMISSION_DENIED -> "Gemini denied access. Enable the Generative Language API for this key."
            AiErrorKind.QUOTA_EXCEEDED -> "Gemini quota exhausted for this key."
            AiErrorKind.MODEL_NOT_FOUND -> "Model '${keyStore.geminiModel}' is not available for this key."
            AiErrorKind.RATE_LIMITED -> "Too many requests. Try again in a moment."
            AiErrorKind.SERVER_ERROR -> "Gemini is having problems (HTTP $code)."
            else -> apiMessage.ifBlank { "Gemini request failed (HTTP $code)." }
        }

        return AiResult.Failure(kind, friendly, code)
    }

    companion object {
        private const val TAG = "GeminiProvider"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
