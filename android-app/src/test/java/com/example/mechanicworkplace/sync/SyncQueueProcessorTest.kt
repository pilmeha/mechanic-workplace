package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.SyncOperationEntity
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileJson
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import com.example.mechanicworkplace.data.remote.onecmobile.PostResultDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveExecutorDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorkRowDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.data.remote.onecmobile.StateRefDto
import com.example.mechanicworkplace.testutil.FakeOrdersDataSource
import com.example.mechanicworkplace.testutil.testOrderCard
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal

class SyncQueueProcessorTest {

    private var now = 100_000L
    private var paused = false
    private val dao = FakeSyncOperationDao()
    private val dataSource = FakeOrdersDataSource()

    @Test
    fun `post is blocked while works are failed`() = runTest {
        dao.insert(operation("works", SyncOperationEntity.TYPE_SAVE_WORKS, status = SyncOperationEntity.STATUS_FAILED))
        dao.insert(operation("post", SyncOperationEntity.TYPE_POST_ORDER, createdAt = 2))
        var postCalls = 0
        dataSource.postOrderHandler = {
            postCalls++
            PostResultDto(true, StateRefDto("s", "Выполнен"))
        }

        processor().run()

        assertEquals(SyncOperationEntity.STATUS_NEEDS_RECONCILIATION, dao.getById("post")?.status)
        assertEquals(SyncQueueRepository.WORKS_NOT_SYNCED, dao.getById("post")?.lastError)
        assertEquals(0, postCalls)
    }

    @Test
    fun `401 marks needs auth and pauses worker`() = runTest {
        dao.insert(operation("post", SyncOperationEntity.TYPE_POST_ORDER))
        dataSource.postOrderHandler = { throw OneCMobileException.Unauthorized() }

        val result = processor().run()

        assertEquals(QueueRunResult.Paused, result)
        assertEquals(SyncOperationEntity.STATUS_NEEDS_AUTH, dao.getById("post")?.status)
        assertTrue(paused)
    }

    @Test
    fun `five attempts end in failed`() = runTest {
        dao.insert(operation("post", SyncOperationEntity.TYPE_POST_ORDER, attempts = 4))
        dataSource.postOrderHandler = { throw OneCMobileException.Network("timeout") }
        dataSource.orderHandler = { testOrderCard(posted = false) }

        processor().run()

        assertEquals(SyncOperationEntity.STATUS_FAILED, dao.getById("post")?.status)
    }

    @Test
    fun `stale sending becomes needs reconciliation`() = runTest {
        dao.insert(
            operation("post", SyncOperationEntity.TYPE_POST_ORDER).copy(
                status = SyncOperationEntity.STATUS_SENDING,
                startedAt = now - SyncQueueRepository.STALE_SENDING_MS - 1
            )
        )

        processor().run()

        assertEquals(
            SyncOperationEntity.STATUS_NEEDS_RECONCILIATION,
            dao.getById("post")?.status
        )
    }

    @Test
    fun `works are sent before post for same order`() = runTest {
        val request = SaveWorksRequest(
            listOf(
                SaveWorkRowDto(
                    lineId = "line-1",
                    workId = "work-1",
                    quantity = BigDecimal("2"),
                    price = BigDecimal("1500.00"),
                    note = "заменено",
                    executors = listOf(SaveExecutorDto("employee-1", 100))
                )
            )
        )
        dao.insert(
            operation(
                "works",
                SyncOperationEntity.TYPE_SAVE_WORKS,
                payloadJson = OneCMobileJson.encodeToString(SaveWorksRequest.serializer(), request)
            )
        )
        dao.insert(operation("post", SyncOperationEntity.TYPE_POST_ORDER, createdAt = 2))
        val calls = mutableListOf<String>()
        dataSource.saveWorksHandler = { _, _ ->
            calls += "works"
            testOrderCard()
        }
        dataSource.orderHandler = { testOrderCard(posted = true) }
        dataSource.postOrderHandler = {
            calls += "post"
            PostResultDto(true, StateRefDto("s", "Выполнен"))
        }

        processor().run()

        assertEquals(listOf("works", "post"), calls)
        assertEquals(SyncOperationEntity.STATUS_DONE, dao.getById("works")?.status)
        assertEquals(SyncOperationEntity.STATUS_DONE, dao.getById("post")?.status)
    }

    @Test
    fun `works mismatch needs reconciliation and is not auto retried`() = runTest {
        val request = SaveWorksRequest(
            listOf(
                SaveWorkRowDto(
                    lineId = "line-1",
                    workId = "work-1",
                    quantity = BigDecimal("2"),
                    price = BigDecimal("1500.00"),
                    note = "заменено",
                    executors = listOf(SaveExecutorDto("employee-1", 100))
                )
            )
        )
        dao.insert(
            operation(
                "works",
                SyncOperationEntity.TYPE_SAVE_WORKS,
                payloadJson = OneCMobileJson.encodeToString(SaveWorksRequest.serializer(), request)
            )
        )
        var saveCalls = 0
        dataSource.saveWorksHandler = { _, _ ->
            saveCalls++
            throw OneCMobileException.Network("timeout")
        }
        dataSource.orderHandler = {
            testOrderCard().copy(
                works = testOrderCard().works.map { it.copy(price = BigDecimal("999.00")) }
            )
        }

        processor().run()
        assertEquals(
            SyncOperationEntity.STATUS_NEEDS_RECONCILIATION,
            dao.getById("works")?.status
        )

        processor().run()
        assertEquals(1, saveCalls)
    }

    @Test
    fun `upload 409 checks photos count and card posted`() = runTest {
        val file = File.createTempFile("queue-photo-", ".jpg").apply {
            writeText("photo")
            deleteOnExit()
        }
        dao.insert(
            operation("photo", SyncOperationEntity.TYPE_UPLOAD_PHOTO).copy(
                localFilePath = file.absolutePath,
                fileName = "photo.jpg",
                contentType = "image/jpeg",
                sizeBytes = file.length()
            )
        )
        dataSource.uploadPhotoHandler = { _, _ ->
            throw OneCMobileException.Conflict("фото")
        }
        dataSource.photosHandler = { _ -> (1..20).map { PhotoMetaDto("p$it", "$it.jpg") } }
        dataSource.orderHandler = { testOrderCard(posted = false) }

        processor().run()

        assertEquals(SyncOperationEntity.STATUS_FAILED, dao.getById("photo")?.status)
        assertEquals(false, file.exists())
    }

    @Test
    fun `pending future operation returns retry later`() = runTest {
        dao.insert(operation("post", SyncOperationEntity.TYPE_POST_ORDER).copy(nextAttemptAt = now + 5_000))

        assertEquals(QueueRunResult.Future(now + 5_000), processor().run())
    }

    private fun processor() = SyncQueueProcessor(
        dao = dao,
        orderCache = FakeOrderCacheDao(),
        photoCache = FakePhotoCacheDao(),
        dataSource = dataSource,
        isPaused = { paused },
        pauseAuth = { paused = true },
        json = OneCMobileJson,
        clock = { now }
    )

    private fun operation(
        id: String,
        type: String,
        status: String = SyncOperationEntity.STATUS_PENDING,
        createdAt: Long = 1,
        attempts: Int = 0,
        payloadJson: String? = null
    ) = SyncOperationEntity(
        id = id,
        orderId = "order-1",
        type = type,
        status = status,
        payloadJson = payloadJson,
        attempts = attempts,
        nextAttemptAt = 0,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
