package com.vasu.ai.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiApiClient(
    private val apiKeyProvider: () -> String?,
    private val model: String = "gemini-3.7-flash"
) {

    fun plan(userCommand: String, context: String): GeminiPlan? {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return null
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", "CURRENT CONTEXT:\n$context\n\nUSER COMMAND:\n$userCommand")))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
                put("candidateCount", 1)
                put("maxOutputTokens", 1200)
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema())
            })
        }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return runCatching {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IOException("Gemini HTTP ${connection.responseCode}")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parsePlan(JSONObject(response))
        }.getOrNull().also { connection.disconnect() }
    }

    private fun parsePlan(response: JSONObject): GeminiPlan? {
        val candidates = response.optJSONArray("candidates") ?: return null
        val text = candidates.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?.takeIf { it.isNotBlank() } ?: return null

        val root = JSONObject(text)
        val steps = root.optJSONArray("steps") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until steps.length()) {
                val item = steps.optJSONObject(i) ?: continue
                add(
                    GeminiStep(
                        action = item.optString("action", ""),
                        target = item.optString("target", ""),
                        value = item.optString("value", "")
                    )
                )
            }
        }
        return GeminiPlan(root.optString("reply", ""), parsed)
    }

    private fun responseSchema(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("reply", JSONObject().put("type", "STRING"))
            put("steps", JSONObject().apply {
                put("type", "ARRAY")
                put("items", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("action", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray(listOf(
                                "open_app", "click_text", "type_text", "scroll_up", "scroll_down",
                                "back", "home", "recents", "volume_up", "volume_down", "mute",
                                "flashlight_on", "flashlight_off", "call_contact", "send_sms",
                                "open_wifi_settings", "open_bluetooth_settings", "open_brightness_settings",
                                "open_dnd_settings", "open_airplane_settings", "open_battery_saver_settings",
                                "open_location_settings"
                            )))
                        })
                        put("target", JSONObject().put("type", "STRING"))
                        put("value", JSONObject().put("type", "STRING"))
                    })
                    put("required", JSONArray(listOf("action", "target", "value")))
                    put("additionalProperties", false)
                })
            })
        })
        put("required", JSONArray(listOf("reply", "steps")))
        put("additionalProperties", false)
    }

    private fun systemPrompt(): String = """
You are VASU AI's autonomous Android operator planner.
Never invent capabilities. Never bypass Android security or permissions.
Return ONLY JSON matching the provided schema.
Use the smallest reliable sequence of actions.
Available actions:
open_app(target=app name), click_text(target=visible UI text), type_text(value=text),
scroll_up, scroll_down, back, home, recents, volume_up, volume_down, mute,
flashlight_on, flashlight_off, call_contact(target=contact name),
send_sms(target=contact name, value=message), and supported Settings actions.
For app messaging, prefer Accessibility sequences such as open_app -> click_text -> type_text -> click_text.
Typing into passwords/secure fields, bypassing locks, or extracting private app data is forbidden.
The application will validate every action before execution.
""".trimIndent()
}
