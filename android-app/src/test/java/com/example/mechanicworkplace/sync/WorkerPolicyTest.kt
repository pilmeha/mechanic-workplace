package com.example.mechanicworkplace.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerPolicyTest {
    @Test
    fun `retry only when eligible operation exists now`() {
        assertTrue(WorkerPolicy.shouldRetry(true))
        assertFalse(WorkerPolicy.shouldRetry(false))
    }
}
