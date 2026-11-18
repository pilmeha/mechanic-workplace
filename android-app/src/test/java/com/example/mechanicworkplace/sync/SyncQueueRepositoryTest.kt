package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.SyncOperationEntity
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileJson
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncQueueRepositoryTest {

    private var now = 10_000L
    private var wakeCalls = 0
    private val dao = FakeSyncOperationDao()

    @Test
    fun `enqueue wakes worker and stores pending op`() = runTest {
        val repository = repository()

        val id = repository.enqueuePostOrder("order-1")

        assertEquals(1, wakeCalls)
        assertEquals(SyncOperationEntity.STATUS_PENDING, dao.getById(id)?.status)
        assertEquals(SyncOperationEntity.TYPE_POST_ORDER, dao.getById(id)?.type)
    }

    @Test
    fun `reconcile marks needs reconciliation as pending marker and wakes`() = runTest {
        dao.items["op"] = SyncOperationEntity(
            id = "op", orderId = "order-1", type = SyncOperationEntity.TYPE_POST_ORDER,
            status = SyncOperationEntity.STATUS_NEEDS_RECONCILIATION,
            createdAt = 1, updatedAt = 1
        )
        val repository = repository()

        repository.reconcile("order-1")

        val op = dao.getById("op")
        assertEquals(SyncOperationEntity.STATUS_PENDING, op?.status)
        assertEquals(SyncQueueRepository.RECONCILE_MARKER, op?.lastError)
        assertEquals(1, wakeCalls)
    }

    @Test
    fun `resume after login resets needs auth and wakes`() = runTest {
        dao.items["op"] = SyncOperationEntity(
            id = "op", orderId = "order-1", type = SyncOperationEntity.TYPE_POST_ORDER,
            status = SyncOperationEntity.STATUS_NEEDS_AUTH,
            createdAt = 1, updatedAt = 1
        )
        val repository = repository()

        repository.resumeAfterLogin()

        assertEquals(SyncOperationEntity.STATUS_PENDING, dao.getById("op")?.status)
        assertEquals(1, wakeCalls)
    }

    @Test
    fun `stale sending is never put back to pending`() = runTest {
        dao.items["op"] = SyncOperationEntity(
            id = "op", orderId = "order-1", type = SyncOperationEntity.TYPE_SAVE_WORKS,
            status = SyncOperationEntity.STATUS_SENDING,
            startedAt = now - SyncQueueRepository.STALE_SENDING_MS - 1,
            createdAt = 1, updatedAt = 1
        )

        repository().resetStaleSending(now)

        assertEquals(
            SyncOperationEntity.STATUS_NEEDS_RECONCILIATION,
            dao.getById("op")?.status
        )
    }

    @Test
    fun `enqueue save works stores payload`() = runTest {
        val repository = repository()
        val id = repository.enqueueSaveWorks("order-1", SaveWorksRequest(emptyList()))

        val op = dao.getById(id)
        assertEquals(SyncOperationEntity.TYPE_SAVE_WORKS, op?.type)
        assertTrue(op?.payloadJson?.contains("works") == true)
    }

    private fun repository() = SyncQueueRepository(
        dao = dao,
        wake = { wakeCalls++ },
        json = OneCMobileJson,
        clock = { now }
    )
}
