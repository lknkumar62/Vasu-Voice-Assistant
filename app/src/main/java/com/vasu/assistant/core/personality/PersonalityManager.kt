package com.vasu.assistant.core.personality

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PersonalityManager - Owns the persisted [PersonalityModel].
 *
 * Stores the model as JSON in the "vasu_personality" SharedPreferences file
 * under the "personality_json" key. Every read and write is guarded and
 * fail-open: any corruption or IO failure falls back to the default model
 * and is logged without message content.
 */
@Singleton
class PersonalityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PersonalityManager"
        private const val PREFS_NAME = "vasu_personality"
        private const val KEY_JSON = "personality_json"
    }

    /**
     * Load the current personality, or the default when nothing valid
     * is stored.
     */
    fun get(): PersonalityModel {
        return try {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null)
                ?: return PersonalityModel()
            parse(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load personality; using default: ${e.message}")
            PersonalityModel()
        }
    }

    /**
     * Learn from one user message: extract signals, apply them, record
     * the interaction, and persist the result.
     */
    fun observe(userMsg: String) {
        try {
            val updated = PersonalityLearner.recordInteraction(
                PersonalityLearner.applySignals(get(), PersonalityLearner.extractSignals(userMsg))
            )
            save(updated)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to observe interaction: ${e.message}")
        }
    }

    /**
     * Render the personality block for [channel] for an LLM system prompt.
     * Fail-open: returns the default block when storage is unreadable.
     */
    fun promptBlock(channel: String = "voice"): String {
        return try {
            PersonalityAdapter.personalityToPrompt(get(), channel)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build personality block: ${e.message}")
            PersonalityAdapter.personalityToPrompt(PersonalityModel(), channel)
        }
    }

    private fun save(model: PersonalityModel) {
        try {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, serialize(model))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save personality: ${e.message}")
        }
    }

    private fun serialize(model: PersonalityModel): String {
        val root = JSONObject()
        root.put("coreTraits", JSONArray(model.coreTraits))
        root.put("learned", prefsToJson(model.learned))
        root.put("learnedKeys", JSONArray(model.learnedKeys.toList()))
        root.put("messageCount", model.messageCount)
        root.put("trustLevel", model.trustLevel)
        root.put("sharedReferences", JSONArray(model.sharedReferences))
        val overrides = JSONObject()
        for ((channel, prefs) in model.channelOverrides) {
            overrides.put(channel, prefsToJson(prefs))
        }
        root.put("channelOverrides", overrides)
        return root.toString()
    }

    private fun prefsToJson(prefs: LearnedPreferences): JSONObject {
        return JSONObject()
            .put("verbosity", prefs.verbosity)
            .put("formality", prefs.formality)
            .put("humorLevel", prefs.humorLevel)
            .put("emojiUsage", prefs.emojiUsage)
            .put("preferredFormat", prefs.preferredFormat)
    }

    private fun parse(raw: String): PersonalityModel {
        val root = JSONObject(raw)
        val overrides = mutableMapOf<String, LearnedPreferences>()
        val overridesJson = root.optJSONObject("channelOverrides")
        if (overridesJson != null) {
            val names = overridesJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                overrides[name] = prefsFromJson(overridesJson.getJSONObject(name))
            }
        }
        return PersonalityModel(
            coreTraits = root.optJSONArray("coreTraits")?.toStringList() ?: listOf("direct", "strategic", "resourceful"),
            learned = root.optJSONObject("learned")?.let(::prefsFromJson) ?: LearnedPreferences(),
            learnedKeys = root.optJSONArray("learnedKeys")?.toStringList()?.toSet() ?: emptySet(),
            messageCount = root.optInt("messageCount", 0),
            trustLevel = root.optInt("trustLevel", 0),
            sharedReferences = root.optJSONArray("sharedReferences")?.toStringList() ?: emptyList(),
            channelOverrides = overrides
        )
    }

    private fun prefsFromJson(json: JSONObject): LearnedPreferences {
        return LearnedPreferences(
            verbosity = json.optInt("verbosity", 5),
            formality = json.optInt("formality", 5),
            humorLevel = json.optInt("humorLevel", 3),
            emojiUsage = json.optBoolean("emojiUsage", false),
            preferredFormat = json.optString("preferredFormat", "adaptive")
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            out.add(optString(i))
        }
        return out
    }
}
