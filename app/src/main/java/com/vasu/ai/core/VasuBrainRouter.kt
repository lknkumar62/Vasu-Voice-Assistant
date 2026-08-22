package com.vasu.ai.core

class VasuBrainRouter(
    private val localIntentEngine: VasuLocalIntentEngine = VasuLocalIntentEngine(),
    private val offlineParser: VasuOfflineCommandParser = VasuOfflineCommandParser()
) {
    data class RoutingResult(
        val mode: VasuBrainMode,
        val localIntent: VasuLocalIntent,
        val localCommands: List<VasuLocalIntent> = emptyList()
    )

    fun route(command: String, preferredMode: VasuBrainMode = VasuBrainMode.AUTO): RoutingResult {
        if (preferredMode == VasuBrainMode.ONLINE) {
            return RoutingResult(VasuBrainMode.ONLINE, VasuLocalIntent.Unknown)
        }

        val parsed = offlineParser.parse(command)
        if (parsed.fullyOfflineSupported) {
            val intents = parsed.commands.map { it.intent }
            return RoutingResult(
                mode = VasuBrainMode.OFFLINE,
                localIntent = intents.firstOrNull() ?: VasuLocalIntent.Unknown,
                localCommands = intents
            )
        }

        val localIntent = localIntentEngine.parse(command)
        if (localIntent != VasuLocalIntent.Unknown && parsed.commands.size <= 1) {
            return RoutingResult(VasuBrainMode.OFFLINE, localIntent, listOf(localIntent))
        }

        if (preferredMode == VasuBrainMode.OFFLINE) {
            return RoutingResult(
                VasuBrainMode.OFFLINE,
                VasuLocalIntent.Unknown,
                parsed.commands.map { it.intent }
            )
        }

        return RoutingResult(
            VasuBrainMode.ONLINE,
            VasuLocalIntent.Unknown,
            parsed.commands.map { it.intent }
        )
    }
}
