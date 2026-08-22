package com.vasu.ai.memory

class VasuMemoryResolver(
    private val store: VasuMemoryStore
) {

    fun resolve(intent: VasuMemoryIntent): String? {
        return when (intent) {
            is VasuMemoryIntent.Remember -> {
                store.remember(intent.key, intent.value)
                "Yaad rakh liya Boss."
            }

            is VasuMemoryIntent.Recall -> {
                store.recall(intent.key)
            }

            is VasuMemoryIntent.Forget -> {
                store.forget(intent.key)
                "Theek hai Boss, bhool gaya."
            }

            VasuMemoryIntent.None -> null
        }
    }
}
