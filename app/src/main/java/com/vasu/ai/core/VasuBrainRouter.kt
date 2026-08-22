package com.vasu.ai.core

class VasuBrainRouter(
    private val localIntentEngine: VasuLocalIntentEngine = VasuLocalIntentEngine(),
    private val offlineParser: VasuOfflineCommandParser = VasuOfflineCommandParser(),
    private val decisionEngine: VasuBrainDecisionEngine =
        VasuBrainDecisionEngine(offlineParser, localIntentEngine)
) {
    data class RoutingResult(
        val mode: VasuBrainMode,
        val localIntent: VasuLocalIntent,
        val localCommands: List<VasuLocalIntent> = emptyList()
    )

    fun route(command: String, preferredMode: VasuBrainMode = VasuBrainMode.AUTO): RoutingResult {
        val decision = decisionEngine.decide(command, preferredMode)

        when (decision) {
            is VasuBrainDecision.Local -> {
                val intents = decision.intents
                println(
                    "VASU_BRAIN_DECISION mode=${preferredMode.name} resolved=LOCAL " +
                        "intentCount=${intents.size}"
                )
                return RoutingResult(
                    mode = VasuBrainMode.OFFLINE,
                    localIntent = intents.firstOrNull() ?: VasuLocalIntent.Unknown,
                    localCommands = intents
                )
            }

            VasuBrainDecision.NoOp -> {
                println(
                    "VASU_BRAIN_DECISION mode=${preferredMode.name} resolved=NO_OP"
                )
                return RoutingResult(
                    mode = VasuBrainMode.OFFLINE,
                    localIntent = VasuLocalIntent.Unknown,
                    localCommands = emptyList()
                )
            }

            is VasuBrainDecision.Online -> {
                println(
                    "VASU_BRAIN_DECISION mode=${preferredMode.name} resolved=ONLINE " +
                        "reason=${decision.reason}"
                )
                return RoutingResult(
                    mode = VasuBrainMode.ONLINE,
                    localIntent = VasuLocalIntent.Unknown,
                    localCommands = offlineParser.parse(command).commands.map { it.intent }
                )
            }
        }
    }

    fun processingResult(
        command: String,
        preferredMode: VasuBrainMode = VasuBrainMode.AUTO
    ): VasuCommandProcessingResult {
        val routing = route(command, preferredMode)
        return when (routing.mode) {
            VasuBrainMode.OFFLINE -> {
                if (routing.localIntent == VasuLocalIntent.Unknown || routing.localCommands.isEmpty()) {
                    VasuCommandProcessingResult(
                        handled = false,
                        onlineRequired = false,
                        actionCount = 0,
                        response = null,
                        reason = "OFFLINE_COMMAND_NOT_UNDERSTOOD"
                    )
                } else {
                    println(
                        "VASU_LOCAL_COMMAND_HANDLED count=${routing.localCommands.size}"
                    )
                    VasuCommandProcessingResult(
                        handled = true,
                        onlineRequired = false,
                        actionCount = routing.localCommands.size,
                        response = "Done",
                        reason = "LOCAL_INTENT"
                    )
                }
            }

            VasuBrainMode.ONLINE -> {
                println("VASU_ONLINE_FALLBACK")
                VasuCommandProcessingResult(
                    handled = false,
                    onlineRequired = true,
                    actionCount = 0,
                    response = null,
                    reason = "GEMINI_REQUIRED"
                )
            }

            VasuBrainMode.AUTO -> error("AUTO should be resolved before returning")
        }
    }
}
