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
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "CURRENT CONTEXT:\n$context\n\nUSER COMMAND:\n$userCommand")))
            }))
            put("generationConfig", JSONObject().apply {
                put("candidateCount", 1)
                put("maxOutputTokens", 1200)
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema())
                put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            })
        }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
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
        val text = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text", "")?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val steps = root.optJSONArray("steps") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until steps.length()) {
                val item = steps.optJSONObject(i) ?: continue
                add(GeminiStep(item.optString("action", ""), item.optString("target", ""), item.optString("value", "")))
            }
        }
        return GeminiPlan(
            reply = root.optString("reply", ""),
            steps = parsed,
            done = root.optBoolean("done", false)
        )
    }

    private fun responseSchema(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("reply", JSONObject().put("type", "STRING"))
            put("done", JSONObject().put("type", "BOOLEAN"))
            put("steps", JSONObject().apply {
                put("type", "ARRAY")
                put("items", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("action", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray(listOf(
                                "open_app", "click_text", "long_click_text", "click_description", "type_text", "press_enter",
                                "scroll_up", "scroll_down", "swipe_up", "swipe_down", "swipe_left", "swipe_right",
                                "back", "home", "recents", "notifications", "lock_screen", "take_screenshot",
                                "volume_up", "volume_down", "mute", "flashlight_on", "flashlight_off",
                                "call_contact", "send_sms", "open_wifi_settings", "open_bluetooth_settings",
                                "open_brightness_settings", "open_dnd_settings", "open_airplane_settings",
                                "open_battery_saver_settings", "open_location_settings"
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
        put("required", JSONArray(listOf("reply", "done", "steps")))
        put("additionalProperties", false)
    }

    private fun systemPrompt(): String = """
You are VASU AI's autonomous Android operator planner.
Never invent capabilities. Never bypass Android security or permissions.
Return ONLY JSON matching the provided schema.
Choose ONE next action only. Set done=true only when the user's command is fully satisfied.
Set done=false whenever another action or verification is required. A reply may describe progress, but it is not completion.
Use zero steps only when no action is needed; if the task is complete, set done=true.
Inspect foreground package, visible screen text, and available notification context before acting.
For multi-step tasks, use fresh context after every successful action and decide the next action from that fresh context.
Available actions: open apps; visible text/content-description click; long click; type; press enter;
scroll; swipe; Back/Home/Recents/notifications/lock/screenshot; volume/mute/flashlight; calls/SMS; supported Settings actions.
For app automation, use Accessibility one action at a time.
Never type passwords, bypass device/app locks, extract private data, or defeat Android security controls.
The application validates every action before execution.
""".trimIndent()
}
