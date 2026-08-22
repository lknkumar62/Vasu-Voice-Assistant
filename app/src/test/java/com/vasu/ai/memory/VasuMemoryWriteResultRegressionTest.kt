package com.vasu.ai.memory

import org.junit.Assert.assertNotNull
import org.junit.Test

class VasuMemoryWriteResultRegressionTest {

    @Test
    fun savedResult_exists() {
        assertNotNull(VasuMemoryWriteResult.Saved)
    }

    @Test
    fun rejectedResult_exists() {
        assertNotNull(VasuMemoryWriteResult.Rejected)
    }
}
