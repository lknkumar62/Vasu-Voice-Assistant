package com.vasu.ai.memory

class VasuMemoryContextProvider(
    private val store: VasuMemoryStore,
    private val formatter: VasuMemoryContextFormatter =
        VasuMemoryContextFormatter()
) {

    fun getContext(maxEntries: Int = 10): String {
        return formatter.format(
            store.snapshot(),
            maxEntries
        )
    }
}
