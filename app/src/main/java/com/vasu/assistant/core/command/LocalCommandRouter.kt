package com.vasu.assistant.core.command

import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A2 (command-router): local-first deterministic routing.
 *
 * Pure Kotlin — no `android.*` imports — so [match] is unit-testable on the
 * JVM and runs in microseconds on-device. Simple device commands matched here
 * skip the LLM round-trip entirely via the hook at the top of
 * `AIOrchestrator.processInput`.
 *
 * Contract:
 * - [match] returns a [LocalCommand] for exactly ONE unambiguous device
 *   command, else `null` and the caller falls through to the existing LLM
 *   path untouched.
 * - `null` is mandatory for: bare ambiguous nouns ("torch", "volume"),
 *   compound commands ("torch on and send sms"), on+off / up+down pairs, and
 *   anything carrying a secondary verb from another domain (send/call/open/
 *   play/…).
 * - Token-based matching (never raw `contains`) so "update" does not match
 *   "date", "sometimes" does not match "time", and "unmute" does not match
 *   "mute".
 * - `requiredRole` is derived from [RiskLevel.LOW.requiredRole] (currently
 *   [UserRole.GUEST] per `SecurityTypes`) — read from the mapping, not
 *   hardcoded — because every routed command is a LOW-risk read or a
 *   side-effect-free-equivalent local device toggle. Time/date are pure
 *   formatting with no side effects at all.
 * - [TOOL_GET_TIME]/[TOOL_GET_DATE] are sentinel tool names with NO
 *   `ToolRouter` dispatch; the orchestrator hook answers them locally via
 *   [LocalCommand.reply]. Everything else names a real `ToolRouter` tool
 *   (`turn_on_torch`, `volume_up`, `set_volume`, `battery_info`, …) and is
 *   executed through `ToolRouter.executeTool` (risk gate included).
 */
/**
 * One unambiguous device command matched locally (no LLM round-trip).
 */
data class LocalCommand(
    val toolName: String,
    val params: Map<String, Any>,
    val requiredRole: UserRole,
    val reply: (String) -> String
)

object LocalCommandRouter {

    /** Sentinel: answered locally, no ToolRouter dispatch exists. */
    const val TOOL_GET_TIME = "local_time"

    /** Sentinel: answered locally, no ToolRouter dispatch exists. */
    const val TOOL_GET_DATE = "local_date"

    /** Single source for the LOW-risk role so SecurityTypes stays authoritative. */
    private val LOW_ROLE: UserRole = RiskLevel.LOW.requiredRole

    // ------------------------------------------------------------------ match

    /**
     * Normalizes raw user text: lowercase/trim, punctuation softening,
     * collapse whitespace, strip leading fillers ("please/vasu/hey/…").
     * Public so unit tests can pin the normalization contract.
     */
    fun normalize(input: String): String {
        var s = input.lowercase(Locale.ROOT).trim()
        if (s.isEmpty()) return ""
        // Soften sentence punctuation (danda included) but KEEP , ; & for
        // compound detection in looksCompound().
        s = s.replace(Regex("[?!._\"'।()\\[\\]%+]"), " ")
        // Strip leading filler words repeatedly ("hey vasu, please …").
        var prev: String
        do {
            prev = s
            s = s.replace(
                Regex("^(?:please|hey|vasu|ok(?:ay)?|ji|arre|aur|hello|namaste)\\b[\\s,]*"),
                ""
            ).trimStart(' ', ',')
        } while (s != prev)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Returns the single deterministic [LocalCommand] for [input], or `null`
     * when the input is ambiguous, compound, carries a secondary verb, or is
     * simply not a covered device command (caller falls through to LLM path).
     */
    fun match(input: String): LocalCommand? {
        val norm = normalize(input)
        if (norm.isEmpty()) return null
        if (looksCompound(norm)) return null

        val tokens: Set<String> = norm.split(Regex("[\\s,;!?&]+"))
            .filter { it.isNotEmpty() }
            .toSet()
        if (tokens.isEmpty()) return null

        val isDeva = input.any { it in '\u0900'..'\u097F' }
        val romanHint = !isDeva && tokens.any { it in HINDI_ROMAN_WORDS }

        // Evaluate every candidate; exactly one may fire (multi-intent → null).
        val candidates = listOfNotNull(
            matchTorch(tokens, isDeva, romanHint),
            matchVolume(tokens, isDeva, romanHint),
            matchBattery(tokens, isDeva, romanHint),
            matchTime(tokens, isDeva, romanHint),
            matchDate(tokens, isDeva, romanHint)
        )
        if (candidates.size != 1) return null
        if (hasSecondaryVerb(tokens)) return null
        return candidates[0]
    }

    // ------------------------------------------------------------- candidates

    private fun matchTorch(tokens: Set<String>, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        val hasNoun = tokens.any { it in TORCH_NOUNS }
        if (!hasNoun) return null
        val on = tokens.any { it in TORCH_ON_CUES }
        val off = tokens.any { it in TORCH_OFF_CUES }
        if (on == off) return null // neither (bare "torch") or both ("on or off")
        return if (on) {
            LocalCommand("turn_on_torch", emptyMap(), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, torch on kar di gayi hai.", "जी, टॉर्च ऑन कर दी गई है।", "Torch is now on.")
            }
        } else {
            LocalCommand("turn_off_torch", emptyMap(), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, torch band kar di gayi hai.", "जी, टॉर्च बंद कर दी गई है।", "Torch is now off.")
            }
        }
    }

    private fun matchVolume(tokens: Set<String>, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        val muteWord = tokens.any { it in MUTE_WORDS }
        val hasNoun = tokens.any { it in VOLUME_NOUNS }
        val up = tokens.any { it in VOLUME_UP_CUES }
        val down = tokens.any { it in VOLUME_DOWN_CUES }
        val max = tokens.any { it in VOLUME_MAX_CUES }
        val muteCue = tokens.any { it in VOLUME_MUTE_CUES }

        var hits = 0
        if (up) hits++
        if (down) hits++
        if (max) hits++
        if (muteWord || (hasNoun && muteCue)) hits++
        // Bare "volume"/"sound" (no action cue) or conflicting cues → ambiguous.
        if (hits != 1) return null

        return when {
            up && hasNoun -> LocalCommand("volume_up", emptyMap(), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, awaaz badha di gayi hai.", "जी, आवाज़ बढ़ा दी गई है।", "Volume increased.")
            }
            down && hasNoun -> LocalCommand("volume_down", emptyMap(), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, awaaz kam kar di gayi hai.", "जी, आवाज़ कम कर दी गई है।", "Volume decreased.")
            }
            max && hasNoun -> LocalCommand("set_volume", mapOf("level" to 100), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, awaaz full kar di gayi hai.", "जी, आवाज़ फुल कर दी गई है।", "Volume set to maximum.")
            }
            muteWord || (hasNoun && muteCue) -> LocalCommand("set_volume", mapOf("level" to 0), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, awaaz mute kar di gayi hai.", "जी, आवाज़ म्यूट कर दी गई है।", "Sound muted.")
            }
            else -> null // cue without its noun, e.g. bare "up" / "full" / "max"
        }
    }

    private fun matchBattery(tokens: Set<String>, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        if (tokens.none { it in BATTERY_CUES }) return null
        // Bare "battery"/"बैटरी" is unambiguous: only one battery command exists.
        return LocalCommand("battery_info", emptyMap(), LOW_ROLE) { summary ->
            batteryReply(summary, isDeva, romanHint)
        }
    }

    private fun matchTime(tokens: Set<String>, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        if (tokens.none { it in TIME_CUES }) return null
        return LocalCommand(TOOL_GET_TIME, emptyMap(), LOW_ROLE) {
            val t = SimpleDateFormat("hh:mm a", Locale("hi", "IN")).format(Date())
            pick(isDeva, romanHint, "Abhi samay $t hai.", "अभी समय $t है।", "The time is $t.")
        }
    }

    private fun matchDate(tokens: Set<String>, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        if (tokens.none { it in DATE_CUES }) return null
        return LocalCommand(TOOL_GET_DATE, emptyMap(), LOW_ROLE) {
            val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN")).format(Date())
            pick(isDeva, romanHint, "Aaj ki tarikh $d hai.", "आज की तारीख $d है।", "Today is $d.")
        }
    }

    // ---------------------------------------------------------------- guards

    /** Compound separators: two commands joined together must fall through. */
    private fun looksCompound(norm: String): Boolean {
        if (norm.contains(" and ") || norm.contains(" then ") ||
            norm.contains(" but ") || norm.contains(" plus ") ||
            norm.contains(" ke baad ") || norm.contains(" ke saath ")
        ) return true
        if (norm.contains(",") || norm.contains(";") || norm.contains("&")) return true
        return false
    }

    /**
     * Verbs/nouns from other tool domains. Any of these means the turn wants
     * something beyond the single local command (e.g. "…and send sms",
     * "torch on karke photo lo") → fall through. Deliberately excludes
     * local-command framing words (karo/kar/kya/hai/batao/please).
     */
    private fun hasSecondaryVerb(tokens: Set<String>): Boolean =
        tokens.any { it in SECONDARY_VERBS }

    // ---------------------------------------------------------------- replies

    private fun pick(isDeva: Boolean, romanHint: Boolean, roman: String, deva: String, eng: String): String =
        when {
            isDeva -> deva
            romanHint -> roman
            else -> eng
        }

    private fun batteryReply(summary: String, isDeva: Boolean, romanHint: Boolean): String {
        val level = Regex("(\\d{1,3})\\s*%").find(summary)?.groupValues?.get(1)?.toIntOrNull()
        if (summary.isBlank() || level == null) {
            if (summary.isNotBlank()) return summary // don't drop an (unparsed) real tool answer
            return pick(
                isDeva, romanHint,
                "Battery ki jaankari prapt nahi ho saki.",
                "बैटरी की जानकारी प्राप्त नहीं हो सकी।",
                "Could not read the battery level."
            )
        }
        val charging = summary.contains("charg", ignoreCase = true)
        return when {
            isDeva -> "आपकी बैटरी $level प्रतिशत है" +
                (if (charging) " और चार्ज हो रहा है" else "") + "।"
            romanHint -> "Aapki battery $level pratishat hai" +
                (if (charging) " aur charge ho raha hai" else "") + "."
            else -> summary // EN canonical text = ToolRouter message ("Battery at 72%, charging")
        }
    }

    // ---------------------------------------------------------------- vocab

    private val TORCH_NOUNS = setOf("torch", "flashlight", "flash", "टॉर्च", "फ्लैशलाइट", "फ्लैश")
    private val TORCH_ON_CUES = setOf("on", "ऑन", "chalu", "चालू", "jalao", "jala", "जलाओ", "जला", "start")
    private val TORCH_OFF_CUES = setOf("off", "ऑफ", "band", "बंद", "bujhao", "bhujao", "बुझाओ", "stop")

    private val VOLUME_NOUNS = setOf(
        "volume", "vol", "sound", "awaz", "awaaz",
        "आवाज़", "आवाज", "वॉल्यूम", "साउंड"
    )
    private val VOLUME_UP_CUES = setOf(
        "up", "badhao", "badao", "barhao", "badha", "bada",
        "increase", "more", "zyada", "tez", "loud", "high", "uncha",
        "बढ़ाओ", "बढाओ", "बढ़ा", "तेज़", "तेज", "ऊंचा"
    )
    private val VOLUME_DOWN_CUES = setOf(
        "down", "kam", "kum", "decrease", "less", "ghatao", "ghata",
        "dheere", "dheemi", "slow", "low",
        "घटाओ", "घटा", "कम", "धीरे", "धीमे"
    )
    private val VOLUME_MAX_CUES = setOf("max", "maximum", "full", "100", "hundred", "फुल", "मैक्स")
    private val VOLUME_MUTE_CUES = setOf("band", "off", "zero", "0", "बंद", "ऑफ")
    private val MUTE_WORDS = setOf("mute", "silent", "म्यूट", "साइलेंट")

    private val BATTERY_CUES = setOf("battery", "बैटरी", "charging")
    private val TIME_CUES = setOf("time", "टाइम", "samay", "समय", "baje", "बजे")
    private val DATE_CUES = setOf("date", "tarikh", "तारीख", "dinank", "दिनांक")

    /** Roman-Hindi cue words: their presence selects the Roman reply variant. */
    private val HINDI_ROMAN_WORDS = setOf(
        "chalu", "jalao", "jala", "band", "bujhao", "bhujao",
        "badhao", "badao", "barhao", "badha", "bada", "kam", "kum",
        "ghatao", "ghata", "dheere", "tez", "zyada", "awaz", "awaaz",
        "samay", "tarikh", "dinank", "baje", "kitni", "kitna", "kitne",
        "kya", "hai", "batao", "bata", "karo", "kar", "kijiye", "aaj"
    )

    private val SECONDARY_VERBS = setOf(
        // messaging / calls
        "send", "sms", "whatsapp", "message", "msg", "forward", "share",
        "call", "phone", "dial", "milao", "bhejo", "bhej", "भेजो",
        // apps / system
        "open", "kholo", "khol", "खोलो", "app", "setting", "settings",
        "wifi", "वाईफाई", "bluetooth", "ब्लूटूथ", "brightness", "ब्राइटनेस",
        "hotspot", "wallpaper", "theme", "language", "bhasha",
        // media / camera
        "play", "chalao", "चलाओ", "bajao", "बजाओ", "baja", "sunao", "सुनाओ",
        "song", "music", "gana", "gaana", "गाना", "video", "वीडियो",
        "photo", "फोटो", "camera", "कैमरा", "selfie", "सेल्फी", "dikhao", "दिखाओ",
        // web / info actions
        "search", "khojo", "खोजो", "google", "youtube", "news", "weather", "mausam",
        // automation / files / misc actions
        "alarm", "अलार्म", "reminder", "yaad", "याद", "lagao", "लगाओ",
        "delete", "hatayo", "हटाओ", "file", "download", "upload",
        "recharge", "bill", "pay", "order", "book", "ticket", "email", "mail",
        "joke", "story"
    )
}
