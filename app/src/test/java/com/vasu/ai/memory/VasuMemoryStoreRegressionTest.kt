package com.vasu.ai.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryStoreRegressionTest {

    @Test
    fun existingMemoryApis_remainAvailable() {
        val methods = VasuMemoryStore::class.java.methods.map { it.name }.toSet()

        assertTrue(methods.contains("add"))
        assertTrue(methods.contains("recent"))
        assertTrue(methods.contains("clear"))
    }

    @Test
    fun preferenceApis_remainAvailable() {
        val methods = VasuMemoryStore::class.java.methods.map { it.name }.toSet()

        assertTrue(methods.contains("rememberPreference"))
        assertTrue(methods.contains("recallPreference"))
        assertTrue(methods.contains("forgetPreference"))
    }
}
