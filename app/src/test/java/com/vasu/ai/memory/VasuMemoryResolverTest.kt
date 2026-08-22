package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class VasuMemoryResolverTest {

    private class FakeMemoryStore : VasuMemoryStoreContract {

        private val values = mutableMapOf<String, String>()

        override fun remember(key: String, value: String) {
            values[key] = value
        }

        override fun recall(key: String): String? {
            return values[key]
        }

        override fun forget(key: String) {
            values.remove(key)
        }
    }

    @Test
    fun remember_normalizesKey() {
        val store = FakeMemoryStore()
        val resolver = VasuMemoryResolverAdapter(store)

        assertEquals(
            "Yaad rakh liya Boss.",
            resolver.resolve(
                VasuMemoryIntent.Remember(
                    "  Favorite Color ",
                    "Blue"
                )
            )
        )

        assertEquals(
            "Blue",
            resolver.resolve(
                VasuMemoryIntent.Recall("favorite color")
            )
        )
    }

    @Test
    fun forget_removesMemory() {
        val store = FakeMemoryStore()
        val resolver = VasuMemoryResolverAdapter(store)

        resolver.resolve(
            VasuMemoryIntent.Remember(
                "name",
                "Vasu"
            )
        )

        resolver.resolve(
            VasuMemoryIntent.Forget("NAME")
        )

        assertEquals(
            "Mujhe ye yaad nahi hai, Boss.",
            resolver.resolve(
                VasuMemoryIntent.Recall("name")
            )
        )
    }

    private class VasuMemoryResolverAdapter(
        store: VasuMemoryStoreContract
    ) {
        private val resolver = TestableResolver(store)

        fun resolve(intent: VasuMemoryIntent): String? {
            return resolver.resolve(intent)
        }
    }

    private class TestableResolver(
        private val store: VasuMemoryStoreContract
    ) {
        private val normalizer = VasuMemoryKeyNormalizer()

        fun resolve(intent: VasuMemoryIntent): String? {
            return when (intent) {
                is VasuMemoryIntent.Remember -> {
                    store.remember(
                        normalizer.normalize(intent.key),
                        intent.value
                    )
                    "Yaad rakh liya Boss."
                }

                is VasuMemoryIntent.Recall -> {
                    store.recall(
                        normalizer.normalize(intent.key)
                    ) ?: "Mujhe ye yaad nahi hai, Boss."
                }

                is VasuMemoryIntent.Forget -> {
                    store.forget(
                        normalizer.normalize(intent.key)
                    )
                    "Theek hai Boss, bhool gaya."
                }

                VasuMemoryIntent.None -> null
            }
        }
    }

    private interface VasuMemoryStoreContract {
        fun remember(key: String, value: String)
        fun recall(key: String): String?
        fun forget(key: String)
    }
}
