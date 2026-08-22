package com.vasu.ai.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryWriteResultTest {

    @Test
    fun savedResult_isMemoryWriteResult() {
        assertTrue(
            VasuMemoryWriteResult.Saved is VasuMemoryWriteResult
        )
    }

    @Test
    fun rejectedResult_isMemoryWriteResult() {
        assertTrue(
            VasuMemoryWriteResult.Rejected is VasuMemoryWriteResult
        )
    }
}
