package com.vasu.ai.core

class VasuBrainRouter(
    private val localIntentEngine: VasuLocalIntentEngine = VasuLocalIntentEngine()
) {
    data class RoutingResult(
        val mode: VasuBrainMode,
        val localIntent: VasuLocalIntent
    )

    fun route(command: String, preferredMode: VasuBrainMode = VasuBrainMode.AUTO): RoutingResult {
        if (preferredMode == VasuBrainMode.ONLINE) {
            return RoutingResult(VasuBrainMode.ONLINE, VasuLocalIntent.Unknown)
        }

        val localIntent = localIntentEngine.parse(command)
        if (localIntent != VasuLocalIntent.Unknown) {
            return RoutingResult(VasuBrainMode.OFFLINE, localIntent)
        }

        if (preferredMode == VasuBrainMode.OFFLINE) {
            return RoutingResult(VasuBrainMode.OFFLINE, VasuLocalIntent.Unknown)
        }

        return RoutingResult(VasuBrainMode.ONLINE, VasuLocalIntent.Unknown)
    }
}
