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
 *   command or FIX-8 offline route (open app / call / back / home / scroll /
 *   click), else `null` and the caller falls through to the existing LLM
 *   path untouched.
 * - `null` is mandatory for: bare ambiguous nouns ("torch", "volume"),
 *   compound commands ("torch on and send sms"), on+off / up+down pairs,
 *   anything carrying a secondary verb from another domain (send/play/…),
 *   and any mixed device+route intent.
 * - Token-based matching (never raw `contains`) so "update" does not match
 *   "date", "sometimes" does not match "time", and "unmute" does not match
 *   "mute".
 * - `requiredRole` is derived from the matching [RiskLevel] mapping in
 *   `SecurityTypes`, never hardcoded: device toggles and screen navigation
 *   are LOW (GUEST), click/open-app are MEDIUM (FRIEND — accessibility and
 *   app launch), and calls are HIGH (BOSS). Time/date are pure formatting
 *   with no side effects at all.
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

    /** FIX-8 routes mirror their ToolRouter risk: click/open-app MEDIUM, calls HIGH. */
    private val MEDIUM_ROLE: UserRole = RiskLevel.MEDIUM.requiredRole
    private val HIGH_ROLE: UserRole = RiskLevel.HIGH.requiredRole

    /**
     * Every tool name (plus the two local sentinels) [match] can return.
     * Kept in exact lockstep with [CommandRegistry.ALL] by
     * `CommandRegistryTest` — the "single registry" invariant.
     */
    val SUPPORTED_TOOLS: Set<String> = setOf(
        "turn_on_torch", "turn_off_torch",
        "volume_up", "volume_down", "set_volume", "battery_info",
        TOOL_GET_TIME, TOOL_GET_DATE,
        "press_back", "press_home", "scroll_up", "scroll_down", "click",
        "open_app", "make_call"
    )

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
        val deviceCandidates = listOfNotNull(
            matchTorch(tokens, isDeva, romanHint),
            matchVolume(tokens, isDeva, romanHint),
            matchBattery(tokens, isDeva, romanHint),
            matchTime(tokens, isDeva, romanHint),
            matchDate(tokens, isDeva, romanHint)
        )
        // FIX-8 offline routes (open app / call / screen navigation / click).
        // These intentionally carry secondary-verb vocabulary of their own
        // ("open", "call", …) so the old veto only guards the device family;
        // the matchers below enforce their own ambiguity rules.
        val routeCandidates = listOfNotNull(
            matchNavigation(tokens, isDeva, romanHint),
            matchClick(norm, isDeva, romanHint),
            matchOpenApp(norm, isDeva, romanHint),
            matchCall(norm, isDeva, romanHint)
        )
        // Cross-family or multi-intent input stays with the LLM path.
        if (deviceCandidates.size + routeCandidates.size != 1) return null
        if (deviceCandidates.isNotEmpty()) {
            if (hasSecondaryVerb(tokens)) return null
            return deviceCandidates[0]
        }
        return routeCandidates[0]
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

    // ----------------------------------------------------------- FIX-8 routes

    /**
     * Screen navigation: back / home / scroll up / down. Exactly one
     * navigational intent may fire; a co-present call verb ("call back mom")
     * or a scroll+nav mix falls through to the LLM path.
     */
    private fun matchNavigation(tokens: Set<String>, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        if (tokens.any { it in CALL_VERBS }) return null
        val wantsBack = tokens.any { it in BACK_CUES }
        val wantsHome = tokens.any { it in HOME_CUES }
        val scrollWord = tokens.any { it in SCROLL_WORDS }
        val dirUp = tokens.any { it in SCROLL_UP_CUES }
        val dirDown = tokens.any { it in SCROLL_DOWN_CUES }
        if (scrollWord && (wantsBack || wantsHome)) return null
        return when {
            scrollWord -> when {
                dirUp == dirDown -> null // bare "scroll" or both directions
                dirUp -> LocalCommand("scroll_up", emptyMap(), LOW_ROLE) {
                    pick(isDeva, romanHint, "Ji, upar scroll kar raha hoon.", "जी, ऊपर स्क्रॉल कर रहा हूँ।", "Scrolling up.")
                }
                else -> LocalCommand("scroll_down", emptyMap(), LOW_ROLE) {
                    pick(isDeva, romanHint, "Ji, neeche scroll kar raha hoon.", "जी, नीचे स्क्रॉल कर रहा हूँ।", "Scrolling down.")
                }
            }
            wantsBack && !wantsHome -> LocalCommand("press_back", emptyMap(), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, wapas ja raha hoon.", "जी, वापस जा रहा हूँ।", "Going back.")
            }
            wantsHome && !wantsBack -> LocalCommand("press_home", emptyMap(), LOW_ROLE) {
                pick(isDeva, romanHint, "Ji, home screen khol raha hoon.", "जी, होम स्क्रीन खोल रहा हूँ।", "Going to the home screen.")
            }
            else -> null
        }
    }

    /**
     * Click a visible element: "click login", "login pe click karo". The
     * target text is handed to the accessibility `click` tool.
     */
    private fun matchClick(norm: String, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        val raw = CLICK_PREFIX.find(norm)?.groupValues?.get(1)
            ?: CLICK_SUFFIX.find(norm)?.groupValues?.get(1)
            ?: return null
        val target = cleanTarget(raw) ?: return null
        return LocalCommand("click", mapOf("text" to target), MEDIUM_ROLE) {
            pick(
                isDeva, romanHint,
                "Ji, \"$target\" pe click kar raha hoon.",
                "जी, \"$target\" पर क्लिक कर रहा हूँ।",
                "Clicking \"$target\"."
            )
        }
    }

    /** Open an app by name: "open whatsapp", "whatsapp kholo". */
    private fun matchOpenApp(norm: String, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        val raw = OPEN_APP_PREFIX.find(norm)?.groupValues?.get(1)
            ?: OPEN_APP_SUFFIX.find(norm)?.groupValues?.get(1)
            ?: return null
        var name = raw.trim().removePrefix("app ").removeSuffix(" app").trim()
        name = name.removePrefix("the ").trim()
        name = cleanTarget(name) ?: return null
        if (name.split(' ').any { it in APP_NAME_CONFLICTS }) return null
        return LocalCommand("open_app", mapOf("app" to name), MEDIUM_ROLE) {
            pick(
                isDeva, romanHint,
                "Ji, $name khol raha hoon.",
                "जी, $name खोल रहा हूँ।",
                "Opening $name."
            )
        }
    }

    /** Call a contact or number: "call mom", "mom ko call karo". */
    private fun matchCall(norm: String, isDeva: Boolean, romanHint: Boolean): LocalCommand? {
        val raw = CALL_PREFIX.find(norm)?.groupValues?.get(1)
            ?: CALL_SUFFIX.find(norm)?.groupValues?.get(1)
            ?: return null
        var target = raw.trim()
        do {
            val before = target
            target = target.replace(Regex("\\s+(?:ko|karo|kar|kro|lagao|laga|please|करो|कर|लगाओ)$"), "").trim()
        } while (target != before)
        target = target.removePrefix("to ").trim()
        target = cleanTarget(target) ?: return null
        if (target.split(' ').any { it in CALL_NAME_CONFLICTS }) return null
        return LocalCommand("make_call", mapOf("contact" to target), HIGH_ROLE) {
            pick(
                isDeva, romanHint,
                "Ji, $target ko call laga raha hoon.",
                "जी, $target को कॉल लगा रहा हूँ।",
                "Calling $target."
            )
        }
    }

    /** Shared cleanup for extracted targets/names; null when unusable. */
    private fun cleanTarget(raw: String): String? {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.length < 2) return null
        if (s in JUNK_TARGETS) return null
        return s
    }

    // ---------------------------------------------------------------- guards

    /** Compound separators: two commands joined together must fall through. */
    private fun looksCompound(norm: String): Boolean {
        if (norm.contains(" and ") || norm.contains(" aur ") ||
            norm.contains(" then ") || norm.contains(" but ") ||
            norm.contains(" plus ") || norm.contains(" ke baad ") ||
            norm.contains(" ke saath ")
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
        "kya", "hai", "batao", "bata", "karo", "kar", "kijiye", "aaj",
        // FIX-8 route cues
        "wapas", "peeche", "pichhe", "kholo", "khol", "upar", "oopar",
        "neeche", "niche", "milao"
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

    // ------------------------------------------------------ FIX-8 route vocab

    /** Extracted targets that are never real (bare pronouns / fillers). */
    private val JUNK_TARGETS = setOf(
        "it", "this", "that", "wo", "ye", "us", "me", "him", "her", "them"
    )

    private val CALL_VERBS = setOf("call", "phone", "dial", "milao", "कॉल", "फोन", "मिलाओ")
    private val MESSAGE_VERBS = setOf(
        "send", "sms", "message", "msg", "bhejo", "bhej", "forward", "share", "भेजो", "मैसेज"
    )
    private val OPEN_VERBS = setOf("open", "kholo", "khol", "launch", "खोलो", "खोल")

    private val BACK_CUES = setOf("back", "wapas", "peeche", "pichhe", "पीछे", "वापस")
    private val HOME_CUES = setOf("home")
    private val SCROLL_WORDS = setOf("scroll", "स्क्रॉल")
    private val SCROLL_UP_CUES = setOf("up", "upar", "oopar", "ऊपर", "top")
    private val SCROLL_DOWN_CUES = setOf("down", "neeche", "niche", "नीचे", "bottom")

    /**
     * Name tokens that signal the utterance belongs to a *different* route or
     * domain ("open whatsapp call mom", "call back mom") — the extracted name
     * is junk and the turn falls through to the LLM path.
     */
    private val APP_NAME_CONFLICTS = CALL_VERBS + MESSAGE_VERBS
    private val CALL_NAME_CONFLICTS = OPEN_VERBS + MESSAGE_VERBS + BACK_CUES + HOME_CUES

    private val CLICK_PREFIX = Regex("^(?:click|tap)\\s+(.+)$")
    private val CLICK_SUFFIX =
        Regex("^(.+?)\\s+(?:pe|par|पर)\\s+(?:click|tap|क्लिक)(?:\\s+(?:karo|kar|kro|करो|कर))?$")
    private val OPEN_APP_PREFIX = Regex("^(?:open|launch)\\s+(.+)$")
    private val OPEN_APP_SUFFIX = Regex(
        "^(.+?)\\s+(?:kholo|khol|kholiye|khol\\s+do|open\\s+(?:karo|kar|kro)|खोलो|खोल|खोल\\s+दो)$"
    )
    private val CALL_PREFIX = Regex("^(?:call|phone|dial)\\s+(.+)$")
    private val CALL_SUFFIX = Regex(
        "^(.+?)\\s+(?:(?:ko|को)\\s*)?(?:call|phone|dial|कॉल|फोन|मिलाओ)\\s*(?:karo|kar|kro|lagao|laga|करो|कर|लगाओ)?$"
    )
}
