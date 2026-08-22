package com.vasu.ai.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local command history and simple persistent key/value memory. */
class VasuMemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("vasu_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun add(command: String, reply: String, handled: Boolean, usedGemini: Boolean) {
        val current = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        current.put(JSONObject().apply {
            put("time", System.currentTimeMillis())
            put("command", command.take(500))
            put("reply", reply.take(1000))
            put("handled", handled)
            put("gemini", usedGemini)
        })
        val trimmed = JSONArray()
        val start = (current.length() - MAX_ITEMS).coerceAtLeast(0)
        for (i in start until current.length()) trimmed.put(current.getJSONObject(i))
        prefs.edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun recent(limit: Int = 20): List<Entry> {
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        val start = (array.length() - limit).coerceAtLeast(0)
        return buildList {
            for (i in array.length() - 1 downTo start) {
                val o = array.optJSONObject(i) ?: continue
                add(Entry(o.optLong("time"), o.optString("command"), o.optString("reply"), o.optBoolean("handled"), o.optBoolean("gemini")))
            }
        }
    }

    fun remember(key: String, value: String) {
        val normalizedKey = key.trim()
        val normalizedValue = value.trim()

        if (normalizedKey.isBlank() || normalizedValue.isBlank()) {
            return
        }

        val safeValue = normalizedValue.take(MAX_VALUE_LENGTH)

        if (safeValue.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()

        prefs.edit()
            .putString(
                "memory:$normalizedKey",
                safeValue
            )
            .putLong(
                "memory:$normalizedKey:updated",
                now
            )
            .apply()
    }

    fun recall(key: String): String? {
        val normalizedKey = key.trim()

        if (normalizedKey.isBlank()) {
            return null
        }

        return prefs.getString(
            "memory:$normalizedKey",
            null
        )
    }

    fun forget(key: String) {
        val normalizedKey = key.trim()

        if (normalizedKey.isBlank()) {
            return
        }

        prefs.edit()
            .remove("memory:$normalizedKey")
            .remove("memory:$normalizedKey:updated")
            .apply()
    }

    fun clear() = prefs.edit().remove(KEY_HISTORY).apply()

    data class Entry(val time: Long, val command: String, val reply: String, val handled: Boolean, val gemini: Boolean)

    private companion object {
        const val KEY_HISTORY = "history"
        const val MAX_ITEMS = 100
        const val MAX_VALUE_LENGTH = 1000
    }
}
