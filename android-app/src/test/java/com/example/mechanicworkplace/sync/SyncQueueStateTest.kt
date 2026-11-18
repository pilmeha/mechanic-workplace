package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.SyncOperationEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncQueueStateTest {

    @Test
    fun `unfinished works includes failed and excludes done`() {
        val statuses = SyncOperationEntity.UNFINISHED_WORKS_STATUSES
        assertTrue(statuses.contains(SyncOperationEntity.STATUS_FAILED))
        assertTrue(statuses.contains(SyncOperationEntity.STATUS_NEEDS_RECONCILIATION))
        assertTrue(statuses.contains(SyncOperationEntity.STATUS_SENDING))
        assertTrue(statuses.contains(SyncOperationEntity.STATUS_PENDING))
        assertFalse(statuses.contains(SyncOperationEntity.STATUS_DONE))
    }

    @Test
    fun `new operation starts pending and immediately eligible`() {
        val operation = SyncOperationEntity(
            id = "id",
            orderId = "order",
            type = SyncOperationEntity.TYPE_POST_ORDER,
            createdAt = 1,
            updatedAt = 1
        )

        assertEquals(SyncOperationEntity.STATUS_PENDING, operation.status)
        assertEquals(0L, operation.nextAttemptAt)
    }

    @Test
    fun `six statuses are distinct`() {
        val statuses = setOf(
            SyncOperationEntity.STATUS_PENDING,
            SyncOperationEntity.STATUS_SENDING,
            SyncOperationEntity.STATUS_NEEDS_RECONCILIATION,
            SyncOperationEntity.STATUS_NEEDS_AUTH,
            SyncOperationEntity.STATUS_FAILED,
            SyncOperationEntity.STATUS_DONE
        )
        assertEquals(6, statuses.size)
    }

    @Test
    fun `five operation types are distinct`() {
        val types = setOf(
            SyncOperationEntity.TYPE_SAVE_WORKS,
            SyncOperationEntity.TYPE_POST_ORDER,
            SyncOperationEntity.TYPE_ADD_DEFECT,
            SyncOperationEntity.TYPE_UPLOAD_PHOTO,
            SyncOperationEntity.TYPE_DELETE_PHOTO
        )
        assertEquals(5, types.size)
    }

    @Test
    fun `stale sending cutoff is five minutes`() {
        assertEquals(5L * 60 * 1000, SyncQueueRepository.STALE_SENDING_MS)
    }

    @Test
    fun `queue markers are stable`() {
        assertEquals("works not synced", SyncQueueRepository.WORKS_NOT_SYNCED)
        assertEquals("baseline older than 24h", SyncQueueRepository.BASELINE_EXPIRED)
        assertEquals("reconcile requested", SyncQueueRepository.RECONCILE_MARKER)
    }
}
