package com.vasu.assistant.core.command

import com.vasu.assistant.core.security.RiskLevel

/**
 * One deterministic voice command VASU can run locally (no LLM round-trip).
 */
data class CommandSpec(
    val id: String,
    val category: String,
    val title: String,
    val examples: List<String>,
    val toolName: String,
    val risk: RiskLevel
)

/**
 * FIX-8 ("single registry"): the one source of truth for local-first voice
 * commands. The Command Library screen renders it, and `CommandRegistryTest`
 * replays every [CommandSpec.examples] entry through [LocalCommandRouter.match]
 * so the catalogue can never drift from the actual matcher.
 */
object CommandRegistry {

    const val CAT_DEVICE = "Device"
    const val CAT_SCREEN = "Screen & navigation"
    const val CAT_APPS = "Apps"
    const val CAT_CALLS = "Calls"

    val ALL: List<CommandSpec> = listOf(
        // ------------------------------------------------------------------ device
        spec("torch_on", CAT_DEVICE, "Turn on the flashlight", "turn_on_torch", RiskLevel.LOW,
            "Turn on the torch", "Torch jalao", "टॉर्च जलाओ"),
        spec("torch_off", CAT_DEVICE, "Turn off the flashlight", "turn_off_torch", RiskLevel.LOW,
            "Turn off the torch", "Torch band karo", "टॉर्च बंद करो"),
        spec("volume_up", CAT_DEVICE, "Volume up", "volume_up", RiskLevel.LOW,
            "Volume up", "Awaaz badhao", "आवाज़ बढ़ाओ"),
        spec("volume_down", CAT_DEVICE, "Volume down", "volume_down", RiskLevel.LOW,
            "Volume down", "Awaaz kam karo", "आवाज़ कम करो"),
        spec("volume_max", CAT_DEVICE, "Volume to maximum", "set_volume", RiskLevel.LOW,
            "Volume full", "Awaaz full karo", "आवाज़ फुल करो"),
        spec("volume_mute", CAT_DEVICE, "Mute sound", "set_volume", RiskLevel.LOW,
            "Mute", "Awaaz band karo", "आवाज़ बंद करो"),
        spec("battery", CAT_DEVICE, "Battery status", "battery_info", RiskLevel.LOW,
            "Battery kitni hai", "बैटरी कितनी है", "Battery status"),
        spec("time", CAT_DEVICE, "Current time", "local_time", RiskLevel.LOW,
            "What time is it", "Samay kya hai", "समय क्या है"),
        spec("date", CAT_DEVICE, "Today's date", "local_date", RiskLevel.LOW,
            "Aaj ki tarikh batao", "What is the date", "आज की तारीख बताओ"),

        // --------------------------------------------------------- screen & nav
        spec("press_back", CAT_SCREEN, "Go back", "press_back", RiskLevel.LOW,
            "Go back", "Wapas chalo", "पीछे जाओ"),
        spec("press_home", CAT_SCREEN, "Go to home screen", "press_home", RiskLevel.LOW,
            "Go home", "Home screen", "Home pe jao"),
        spec("scroll_down", CAT_SCREEN, "Scroll down", "scroll_down", RiskLevel.LOW,
            "Scroll down", "Neeche scroll karo", "नीचे स्क्रॉल करो"),
        spec("scroll_up", CAT_SCREEN, "Scroll up", "scroll_up", RiskLevel.LOW,
            "Scroll up", "Upar scroll karo", "ऊपर स्क्रॉल करो"),
        spec("click", CAT_SCREEN, "Click on visible text", "click", RiskLevel.MEDIUM,
            "Click login", "Login pe click karo", "लॉगिन पर क्लिक करो"),

        // -------------------------------------------------------------------- apps
        spec("open_app", CAT_APPS, "Open an app by name", "open_app", RiskLevel.MEDIUM,
            "Open whatsapp", "Whatsapp kholo", "Whatsapp खोलो"),

        // ------------------------------------------------------------------- calls
        spec("make_call", CAT_CALLS, "Call a contact or number", "make_call", RiskLevel.HIGH,
            "Call mom", "Mom ko call karo", "मम्मी को कॉल करो")
    )

    /** Registry grouped for the UI, in declaration order. */
    fun byCategory(): List<Pair<String, List<CommandSpec>>> = ALL.groupBy { it.category }.toList()

    private fun spec(
        id: String,
        category: String,
        title: String,
        toolName: String,
        risk: RiskLevel,
        vararg examples: String
    ): CommandSpec = CommandSpec(id, category, title, examples.toList(), toolName, risk)
}
