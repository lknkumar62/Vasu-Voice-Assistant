package com.vasu.ai.core

class VasuConversationContextResolver(
    private val store: VasuConversationContextStore
) {
    fun resolveForCommand(command: String): VasuConversationContext {
        store.expireIfNeeded()
        println(
            "VASU_CONTEXT_RESOLUTION " +
                "hasContext=${store.hasActiveContext()} " +
                "followUp=${store.isFollowUpCandidate(command)}"
        )
        return store.get()
    }
}
