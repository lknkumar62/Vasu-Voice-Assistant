package com.vasu.ai.core

class VasuBrainDecisionEngine(
    private val offlineParser: VasuOfflineCommandParser = VasuOfflineCommandParser(),
    private val localIntentEngine: VasuLocalIntentEngine = VasuLocalIntentEngine()
) {
    fun decide(
        command: String,
        mode: VasuBrainMode
    ): VasuBrainDecision {
        val parsed = offlineParser.parse(command)
        val local = localIntentEngine.parse(command)

        return when (mode) {
            VasuBrainMode.OFFLINE -> {
                if (parsed.fullyOfflineSupported) {
                    VasuBrainDecision.Local(parsed.commands.map { it.intent })
                } else {
                    println("VASU_OFFLINE_COMMAND_UNSUPPORTED")
                    VasuBrainDecision.NoOp
                }
            }

            VasuBrainMode.ONLINE -> {
                println("VASU_BRAIN_DECISION mode=ONLINE reason=ONLINE_MODE")
                VasuBrainDecision.Online("ONLINE_MODE")
            }

            VasuBrainMode.AUTO -> {
                if (parsed.fullyOfflineSupported) {
                    VasuBrainDecision.Local(parsed.commands.map { it.intent })
                } else if (local != VasuLocalIntent.Unknown) {
                    println("VASU_BRAIN_DECISION mode=ONLINE reason=INCOMPLETE_LOCAL_PARSE")
                    VasuBrainDecision.Online("INCOMPLETE_LOCAL_PARSE")
                } else {
                    println("VASU_BRAIN_DECISION mode=ONLINE reason=LOCAL_INTENT_NOT_FOUND")
                    VasuBrainDecision.Online("LOCAL_INTENT_NOT_FOUND")
                }
            }
        }
    }
}
