package com.vasu.ai.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small encrypted-by-app-storage boundary for recent VASU interactions. */
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

    fun clear() = prefs.edit().remove(KEY_HISTORY).apply()

    data class Entry(val time: Long, val command: String, val reply: String, val handled: Boolean, val gemini: Boolean)

    private companion object {
        const val KEY_HISTORY = "history"
        const val MAX_ITEMS = 100
    }
}
