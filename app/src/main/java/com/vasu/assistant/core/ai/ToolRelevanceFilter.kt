package com.vasu.assistant.core.ai

import java.util.Locale

/**
 * ToolRelevanceFilter - keyword pre-filter over the LLM tool list.
 *
 * Port of the Jarvis tool-relevance selection heuristic
 * (jarvis: src/actions/tools/tool-relevance/selection.ts) trimmed to what
 * VASU needs: pick the tools a turn plausibly needs from the conversation
 * text so the model is offered a small relevant subset instead of the full
 * registry on every turn.
 *
 * Design rules (shared with the Jarvis source):
 * - The selection is only ever a CANDIDATE. It may be wrong; the failure
 *   direction is always fail-open ([decide] returns the full list when the
 *   signal is weak, contradictory, or missing).
 * - Pure Kotlin, no android.* imports, so it stays unit-testable on the JVM.
 * - Additive only: callers keep passing the full list on any doubt.
 */
object ToolRelevanceFilter {

    /** How much conversation tail the selection reads, in characters. */
    const val SELECTION_WINDOW_CHARS = 8000

    /** Filter policy. Kept for parity with the Jarvis policy module. */
    data class Policy(val enabled: Boolean = true, val maxTools: Int = 24)

    /** One trigger group: tool names offered when any keyword hits. */
    data class TriggerGroup(
        val tools: Set<String>,
        val keywords: Set<String>
    )

    /**
     * Group -> (tools, trigger words). Built from the actual [ToolRouter]
     * tool names. Keywords are lowercase; matching is whole-word plus a
     * plain plural (s|es), EN plus a few Hindi forms.
     */
    val TRIGGER_GROUPS: Map<String, TriggerGroup> = mapOf(
        "torch" to TriggerGroup(
            tools = setOf("turn_on_torch", "turn_off_torch"),
            keywords = setOf("torch", "flashlight", "mashaal", "light", "टॉर्च", "फ्लैशलाइट")
        ),
        "volume" to TriggerGroup(
            tools = setOf("volume_up", "volume_down", "set_volume", "media_play_pause", "media_next"),
            keywords = setOf(
                "volume", "awaz", "आवाज़", "sound", "loud", "mute",
                "music", "speaker", "वॉल्यूम", "play"
            )
        ),
        "battery" to TriggerGroup(
            tools = setOf("battery_info", "device_info", "storage_info"),
            keywords = setOf(
                "battery", "charging", "charger", "बैटरी",
                "device", "storage", "space", "ram"
            )
        ),
        "messaging" to TriggerGroup(
            tools = setOf("open_whatsapp", "make_call", "send_message", "lookup_contact"),
            keywords = setOf(
                "call", "phone", "dial", "whatsapp",
                "sms", "message", "contact", "number"
            )
        ),
        "alarm" to TriggerGroup(
            tools = setOf("create_alarm"),
            keywords = setOf(
                "alarm", "time", "samay", "समय",
                "date", "tarikh", "तारीख", "reminder"
            )
        ),
        "web" to TriggerGroup(
            tools = setOf("search_web"),
            keywords = setOf(
                "search", "google", "web", "internet",
                "online", "website", "news", "weather"
            )
        ),
        "screen" to TriggerGroup(
            tools = setOf(
                "read_screen", "click", "type_text", "press_back",
                "press_home", "scroll_down", "scroll_up", "open_app"
            ),
            keywords = setOf(
                "screen", "click", "tap", "scroll",
                "open", "button", "type", "press"
            )
        ),
        "camera" to TriggerGroup(
            tools = setOf("take_photo", "ocr_extract", "describe_image", "scan_qr"),
            keywords = setOf(
                "photo", "camera", "picture", "कैमरा",
                "image", "scan", "screenshot", "tasveer"
            )
        ),
        "files" to TriggerGroup(
            tools = setOf("browse_files", "search_files", "delete_file"),
            keywords = setOf("file", "folder", "directory", "download", "path", "document")
        ),
        "missions" to TriggerGroup(
            tools = setOf("run_mission", "read_notifications"),
            keywords = setOf(
                "mission", "automation", "notification",
                "alert", "routine", "सूचना"
            )
        )
    )

    /** A bare URL anywhere in the text. Selects the web group. */
    private val URL_RE = Regex("""https?://\S+|\bwww\.\S+""", RegexOption.IGNORE_CASE)

    /** A bare domain with no scheme ("go to github.com"). Selects web. */
    private val DOMAIN_RE =
        Regex("""\b[a-z0-9-]{1,63}\.(?:com|org|net|io|dev|app|edu|gov)\b""", RegexOption.IGNORE_CASE)

    /** A path-looking token or a fenced code block. Selects the files group. */
    private val PATH_RE = Regex("""(^|\s)[~.]?[/\\][\w.\-/\\]+|```""")

    private val wordCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    /**
     * Whole word plus the plain plural. Boundaries are script-aware
     * (letter/mark/digit on either side blocks the match) so Devanagari
     * keywords match and "update" never matches "date".
     */
    private fun wordRegex(word: String): Regex {
        return wordCache.getOrPut(word) {
            Regex(
                "(?<![\\p{L}\\p{M}\\p{Nd}])" + Regex.escape(word) +
                    "(?:s|es)?(?![\\p{M}\\p{L}\\p{Nd}])"
            )
        }
    }

    /**
     * Names the conversation makes relevant, from the trigger table alone.
     *
     * Reads at most the last [SELECTION_WINDOW_CHARS] characters, newest
     * message first. Names outside [allNames] are left out so "matched"
     * means "matched something the caller can actually offer".
     *
     * Returns emptySet() when there is no strong signal; that is not "offer
     * nothing", it is the caller-visible "no strong signal" marker and the
     * caller ([decide]) fails open to the full list.
     */
    fun selectForConversation(messages: List<String>, allNames: Set<String>): Set<String> {
        val newestFirst = ArrayList<String>()
        var budget = SELECTION_WINDOW_CHARS
        for (i in messages.indices.reversed()) {
            if (budget <= 0) break
            if (newestFirst.isNotEmpty()) {
                budget -= 1 // the newline joining this part to the newer one
                if (budget <= 0) break
            }
            val text = messages[i]
            val lower = text.lowercase(Locale.ROOT)
            newestFirst.add(if (lower.length > budget) lower.takeLast(budget) else lower)
            budget -= text.length
        }
        val window = newestFirst.reversed().joinToString("\n")
        if (window.isBlank()) return emptySet()

        val out = LinkedHashSet<String>()
        val usable = { name: String -> allNames.contains(name) }
        for ((_, group) in TRIGGER_GROUPS) {
            if (group.keywords.any { wordRegex(it).containsMatchIn(window) }) {
                group.tools.filterTo(out) { usable(it) }
            }
        }
        if (URL_RE.containsMatchIn(window) || DOMAIN_RE.containsMatchIn(window)) {
            TRIGGER_GROUPS["web"]?.tools?.filterTo(out) { usable(it) }
        }
        if (PATH_RE.containsMatchIn(window)) {
            TRIGGER_GROUPS["files"]?.tools?.filterTo(out) { usable(it) }
        }
        return out
    }

    /**
     * Narrow [all] to [wanted], failing open.
     *
     * - [wanted] empty (no strong signal) -> [all], unchanged.
     * - The narrowed set covers [all] anyway -> [all], unchanged.
     * - Narrowing to nothing -> [all]; an empty tool list is never returned.
     * - Otherwise the subset, in [all]'s order, plus a tiny floor of
     *   always-keep tools (names containing "help" or "unknown"; VASU
     *   registers none today, so the floor is empty and adds nothing).
     */
    fun decide(all: List<ToolDefinition>, wanted: Set<String>): List<ToolDefinition> {
        if (wanted.isEmpty() || all.isEmpty()) return all
        val wantedLower = wanted.map { it.lowercase(Locale.ROOT) }.toSet()
        val subset = all.filter { wantedLower.contains(it.name.lowercase(Locale.ROOT)) }
        if (subset.size >= all.size) return all
        if (subset.isEmpty()) return all
        val floor = all.filter {
            val n = it.name.lowercase(Locale.ROOT)
            n.contains("help") || n.contains("unknown")
        }
        val keep = (subset.map { it.name } + floor.map { it.name }).toSet()
        return all.filter { keep.contains(it.name) }
    }
}
