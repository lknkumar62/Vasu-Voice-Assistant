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
 * Real Anthropic Claude and OmniRoute AI Provider.
 *
 * Requirements:
 * - Real HTTP requests via OkHttp to Anthropic Messages API or OmniRoute base URL.
 * - API key loaded from SecureKeyStore or runtime environment variables.
 * - Accurate HTTP error taxonomy (401, 402, 403, 404, 429, 5xx).
 * - Full support for tool calling in Anthropic JSON schema format.
 * - Real connection testing before claiming the provider or model works.
 */
@Singleton
class ClaudeProvider @Inject constructor(
    private val keyStore: SecureKeyStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        prompt: String,
        systemPrompt: String = "",
        history: List<ChatMessage> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        temperature: Float = 0.7f,
        maxTokens: Int = 1024,
        overrideModel: String? = null
    ): AiResult = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getClaudeKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "Claude/OmniRoute API key is missing. Add your API key in Settings."
            )
        }

        val model = overrideModel?.takeIf { it.isNotBlank() } ?: keyStore.claudeModel
        val baseUrl = keyStore.claudeBaseUrl.trimEnd('/')

        try {
            val payload = buildMessagesPayload(
                model = model,
                systemPrompt = systemPrompt,
                prompt = prompt,
                history = history,
                tools = tools,
                temperature = temperature,
                maxTokens = maxTokens
            )

            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val code = response.code

                if (!response.isSuccessful) {
                    val errorKind = classifyHttpError(code, body)
                    val errorMsg = parseErrorMessage(body, code, model)
                    keyStore.lastError = errorMsg
                    return@withContext AiResult.Failure(errorKind, errorMsg, code)
                }

                keyStore.activeModel = model
                keyStore.lastSuccessfulConnection = System.currentTimeMillis()
                keyStore.lastError = null

                parseResponse(body)
            }
        } catch (e: UnknownHostException) {
            AiResult.Failure(AiErrorKind.OFFLINE, "No internet connection. Offline commands are active.")
        } catch (e: SocketTimeoutException) {
            AiResult.Failure(AiErrorKind.TIMEOUT, "Request to Claude timed out. Please try again.")
        } catch (e: IOException) {
            AiResult.Failure(AiErrorKind.SERVER_ERROR, "Network error reaching Claude: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling Claude", e)
            AiResult.Failure(AiErrorKind.UNKNOWN, "Unexpected error: ${e.message}")
        }
    }

    suspend fun testConnection(): AiResult = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getClaudeKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "Claude API key is not configured in Settings or environment."
            )
        }

        val model = keyStore.claudeModel
        val baseUrl = keyStore.claudeBaseUrl.trimEnd('/')

        try {
            val testPayload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 5)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", "VASU ping")
                }))
            }

            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(testPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    keyStore.lastSuccessfulConnection = System.currentTimeMillis()
                    keyStore.lastError = null
                    AiResult.Text("Connected to $model successfully.")
                } else {
                    val kind = classifyHttpError(response.code, body)
                    val msg = parseErrorMessage(body, response.code, model)
                    keyStore.lastError = msg
                    AiResult.Failure(kind, msg, response.code)
                }
            }
        } catch (e: Exception) {
            AiResult.Failure(AiErrorKind.OFFLINE, "Failed to connect to Claude: ${e.message}")
        }
    }

    private fun buildMessagesPayload(
        model: String,
        systemPrompt: String,
        prompt: String,
        history: List<ChatMessage>,
        tools: List<ToolDefinition>,
        temperature: Float,
        maxTokens: Int
    ): JSONObject {
        val payload = JSONObject()
        payload.put("model", model)
        payload.put("max_tokens", maxTokens)
        if (temperature in 0.0f..1.0f) {
            payload.put("temperature", temperature.toDouble())
        }

        if (systemPrompt.isNotBlank()) {
            payload.put("system", systemPrompt)
        }

        val messagesArray = JSONArray()
        for (msg in history) {
            if (msg.content.isNotBlank() && (msg.role == "user" || msg.role == "assistant")) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        if (prompt.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }
        payload.put("messages", messagesArray)

        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            for (tool in tools) {
                val toolObj = JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    val inputSchema = JSONObject().apply {
                        put("type", "object")
                        val properties = JSONObject()
                        val requiredList = JSONArray()
                        for (param in tool.parameters) {
                            properties.put(param.name, JSONObject().apply {
                                put("type", mapParamTypeToClaude(param.type))
                                put("description", param.description)
                            })
                            if (param.required) {
                                requiredList.put(param.name)
                            }
                        }
                        put("properties", properties)
                        if (requiredList.length() > 0) {
                            put("required", requiredList)
                        }
                    }
                    put("input_schema", inputSchema)
                }
                toolsArray.put(toolObj)
            }
            payload.put("tools", toolsArray)
        }

        return payload
    }

    private fun parseResponse(body: String): AiResult {
        return try {
            val json = JSONObject(body)
            val contentArray = json.optJSONArray("content")
                ?: return AiResult.Failure(AiErrorKind.MALFORMED_RESPONSE, "Empty response from Claude")

            var textContent = ""
            var functionCall: AiResult.FunctionCall? = null

            val usage = json.optJSONObject("usage")
            val tokensUsed = (usage?.optInt("input_tokens") ?: 0) + (usage?.optInt("output_tokens") ?: 0)

            for (i in 0 until contentArray.length()) {
                val block = contentArray.optJSONObject(i) ?: continue
                val type = block.optString("type")
                if (type == "text") {
                    textContent += block.optString("text")
                } else if (type == "tool_use") {
                    val toolName = block.optString("name")
                    val inputObj = block.optJSONObject("input")
                    val argsMap = mutableMapOf<String, Any>()
                    if (inputObj != null) {
                        for (key in inputObj.keys()) {
                            argsMap[key] = inputObj.get(key)
                        }
                    }
                    functionCall = AiResult.FunctionCall(toolName, argsMap, tokensUsed)
                    break
                }
            }

            functionCall ?: AiResult.Text(textContent.trim(), tokensUsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Claude response", e)
            AiResult.Failure(AiErrorKind.MALFORMED_RESPONSE, "Could not parse Claude response: ${e.message}")
        }
    }

    private fun classifyHttpError(code: Int, body: String): AiErrorKind = when (code) {
        401 -> AiErrorKind.INVALID_KEY
        402 -> AiErrorKind.QUOTA_EXCEEDED
        403 -> AiErrorKind.PERMISSION_DENIED
        404 -> AiErrorKind.MODEL_NOT_FOUND
        429 -> AiErrorKind.RATE_LIMITED
        in 500..599 -> AiErrorKind.SERVER_ERROR
        else -> {
            val lower = body.lowercase()
            when {
                lower.contains("quota") || lower.contains("credit") -> AiErrorKind.QUOTA_EXCEEDED
                lower.contains("rate limit") -> AiErrorKind.RATE_LIMITED
                lower.contains("model") && lower.contains("not found") -> AiErrorKind.MODEL_NOT_FOUND
                lower.contains("auth") || lower.contains("key") -> AiErrorKind.INVALID_KEY
                else -> AiErrorKind.UNKNOWN
            }
        }
    }

    private fun parseErrorMessage(body: String, code: Int, model: String): String {
        val parsedMsg = try {
            val errObj = JSONObject(body).optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        if (parsedMsg != null) return parsedMsg

        return when (code) {
            401 -> "Claude API key is invalid or unauthorized."
            402 -> "Payment required / credit balance too low on Claude/OmniRoute."
            403 -> "Claude API key does not have permission to access this resource."
            404 -> "Model '$model' was not found or is unavailable through this provider."
            429 -> "Claude API rate limit reached. Please wait a moment."
            in 500..599 -> "Claude server error (HTTP $code). Please retry shortly."
            else -> "Claude API request failed with HTTP code $code."
        }
    }

    private fun mapParamTypeToClaude(type: String): String = when (type.lowercase()) {
        "int", "integer", "long" -> "integer"
        "float", "double", "number" -> "number"
        "bool", "boolean" -> "boolean"
        "array", "list" -> "array"
        "object", "map" -> "object"
        else -> "string"
    }

    companion object {
        private const val TAG = "ClaudeProvider"
    }
}
