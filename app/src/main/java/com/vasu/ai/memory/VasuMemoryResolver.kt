package com.vasu.ai.memory

class VasuMemoryResolver(
    private val store: VasuMemoryStore,
    private val keyNormalizer: VasuMemoryKeyNormalizer = VasuMemoryKeyNormalizer()
) {

    fun resolve(intent: VasuMemoryIntent): String? {
        return when (intent) {
            is VasuMemoryIntent.Remember -> {
                val key = keyNormalizer.normalize(intent.key)

                if (key.isBlank()) {
                    return "Kya yaad rakhna hai, Boss?"
                }

                store.remember(key, intent.value)

                "Yaad rakh liya Boss."
            }

            is VasuMemoryIntent.Recall -> {
                val key = keyNormalizer.normalize(intent.key)

                if (key.isBlank()) {
                    return "Kya yaad karna hai, Boss?"
                }

                store.recall(key)
                    ?: "Mujhe ye yaad nahi hai, Boss."
            }

            is VasuMemoryIntent.Forget -> {
                val key = keyNormalizer.normalize(intent.key)

                if (key.isBlank()) {
                    return "Kya bhoolna hai, Boss?"
                }

                store.forget(key)

                "Theek hai Boss, bhool gaya."
            }

            VasuMemoryIntent.None -> null
        }
    }
}
