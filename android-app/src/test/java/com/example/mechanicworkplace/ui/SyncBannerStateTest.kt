package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.auth.QueueGateState
import com.example.mechanicworkplace.data.local.StatusCount
import com.example.mechanicworkplace.data.local.SyncOperationEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncBannerStateTest {

    @Test
    fun `maps queue counts and auth gate`() {
        val state = syncBannerState(
            listOf(
                StatusCount(SyncOperationEntity.STATUS_PENDING, 2),
                StatusCount(SyncOperationEntity.STATUS_NEEDS_RECONCILIATION, 1),
                StatusCount(SyncOperationEntity.STATUS_FAILED, 3),
                StatusCount(SyncOperationEntity.STATUS_NEEDS_AUTH, 4)
            ),
            QueueGateState.Open
        )

        assertTrue(state.visible)
        assertTrue(state.needsAuth)
        assertEquals(2, state.pending)
        assertEquals(1, state.needsReconciliation)
        assertEquals(3, state.failed)
    }

    @Test
    fun `paused gate alone shows banner`() {
        val state = syncBannerState(emptyList(), QueueGateState.Paused("NEEDS_AUTH"))
        assertTrue(state.visible)
        assertTrue(state.needsAuth)
    }

    @Test
    fun `empty queue hides banner`() {
        assertFalse(syncBannerState(emptyList(), QueueGateState.Open).visible)
    }
}
