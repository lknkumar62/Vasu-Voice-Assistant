package com.vasu.assistant.core.awareness

/**
 * Awareness roles + goal fuzzy matcher, ported from Jarvis
 * (`roles/activity-observer.yaml`, `src/goals/awareness-bridge.ts`).
 *
 * Pure Kotlin, no `android.*` imports.
 */

/** A background awareness role with an authority level and action scopes. */
data class AwarenessRole(
    val id: String,
    val name: String,
    val authorityLevel: Int,
    val autonomous: List<String>,
    val approvalRequired: List<String>,
    val backgroundInstructions: String
)

/**
 * Activity Observer (Jarvis `activity_observer`, authority_level 2):
 * passive monitoring — watches activity, learns patterns, never acts
 * without approval.
 */
val ACTIVITY_OBSERVER = AwarenessRole(
    id = "activity_observer",
    name = "Activity Observer",
    authorityLevel = 2,
    autonomous = listOf(
        "Log observed activities to the vault",
        "Update user preference models",
        "Generate daily activity summaries"
    ),
    approvalRequired = listOf(
        "Share observations with other agents",
        "Send notifications or suggestions to the user",
        "Modify any system settings"
    ),
    backgroundInstructions = "Every 30 minutes, passively check: active applications and " +
        "windows; the user's current context (working, browsing, break); time spent on " +
        "different tasks; interruptions and context switches. " +
        "Do NOT interrupt the user. Only log observations. " +
        "At the end of each day, generate a summary of patterns noticed."
)

/**
 * Returns true when the role is well-formed: non-blank id/name,
 * authority level in 1..10, and non-empty autonomous/approval lists.
 */
fun validate(role: AwarenessRole): Boolean =
    role.id.isNotBlank() &&
        role.name.isNotBlank() &&
        role.authorityLevel in 1..10 &&
        role.autonomous.isNotEmpty() &&
        role.approvalRequired.isNotEmpty()

/** Minimum fuzzy score for a goal/activity association (Jarvis bridge). */
const val MATCH_THRESHOLD = 0.15

/** Minimum matched terms for a goal/activity association (Jarvis bridge). */
const val MIN_MATCHED = 2

/** Small stop-word set filtered before matching (subset of Jarvis STOP_WORDS). */
private val STOP_WORDS = setOf(
    "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "is", "are"
)

/** Lowercase alphanumeric tokens of length >= 3 minus stop words. */
private fun tokenize(text: String): List<String> =
    text.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in STOP_WORDS }

/**
 * Terms of [activityText] that match [goalText], via exact match plus
 * substring match (both sides length >= 4, either direction contains),
 * deduplicated. Mirrors Jarvis `fuzzyMatch` matched-terms half.
 */
fun matchedTerms(goalText: String, activityText: String): List<String> {
    val goalWords = tokenize(goalText)
    if (goalWords.isEmpty()) return emptyList()
    val goalSet = goalWords.toSet()
    val matched = mutableListOf<String>()
    for (word in tokenize(activityText)) {
        if (word in goalSet) {
            matched.add(word)
        } else {
            for (gw in goalSet) {
                if (gw.length >= 4 && word.length >= 4 &&
                    (gw.contains(word) || word.contains(gw))
                ) {
                    matched.add(word)
                    break
                }
            }
        }
    }
    return matched.toSet().toList()
}

/**
 * Fuzzy score of [activityText] against [goalText]: unique matched terms
 * divided by goal token count (0 when the goal has no tokens).
 * Mirrors Jarvis `fuzzyMatch` score half.
 */
fun matchScore(goalText: String, activityText: String): Double {
    val goalWords = tokenize(goalText)
    if (goalWords.isEmpty()) return 0.0
    return matchedTerms(goalText, activityText).size.toDouble() / goalWords.size
}

/**
 * True when the activity plausibly relates to the goal: score >=
 * [MATCH_THRESHOLD] AND matched terms >= [MIN_MATCHED]. A match is a
 * possible association, never proof of progress (Jarvis bridge semantics).
 */
fun isGoalMatch(goalText: String, activityText: String): Boolean =
    matchScore(goalText, activityText) >= MATCH_THRESHOLD &&
        matchedTerms(goalText, activityText).size >= MIN_MATCHED
