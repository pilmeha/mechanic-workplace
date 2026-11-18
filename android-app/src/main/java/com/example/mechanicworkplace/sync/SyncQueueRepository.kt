package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.SyncOperationDao
import com.example.mechanicworkplace.data.local.StatusCount
import com.example.mechanicworkplace.data.local.SyncOperationEntity
import kotlinx.coroutines.flow.Flow
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_PENDING
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_ADD_DEFECT
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_DELETE_PHOTO
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_POST_ORDER
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_SAVE_WORKS
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_UPLOAD_PHOTO
import com.example.mechanicworkplace.data.photo.PreparedPhoto
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class SyncQueueRepository(
    private val dao: SyncOperationDao,
    private val wake: () -> Unit,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun enqueueSaveWorks(orderId: String, request: SaveWorksRequest): String =
        enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                type = TYPE_SAVE_WORKS,
                payloadJson = json.encodeToString(SaveWorksRequest.serializer(), request),
                createdAt = clock(),
                updatedAt = clock()
            )
        )

    suspend fun enqueuePostOrder(orderId: String): String =
        enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                type = TYPE_POST_ORDER,
                createdAt = clock(),
                updatedAt = clock()
            )
        )

    suspend fun enqueueAddDefect(
        orderId: String,
        request: AddDefectRequest,
        beforeCount: Int
    ): String =
        enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                type = TYPE_ADD_DEFECT,
                payloadJson = json.encodeToString(AddDefectRequest.serializer(), request),
                baselineJson = """{"beforeCount":$beforeCount}""",
                createdAt = clock(),
                updatedAt = clock()
            )
        )

    suspend fun enqueueUploadPhoto(orderId: String, photo: PreparedPhoto): String {
        val sha256 = withContext(ioDispatcher) {
            MessageDigest.getInstance("SHA-256")
                .digest(photo.file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
        return enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                type = TYPE_UPLOAD_PHOTO,
                localFilePath = photo.file.absolutePath,
                fileName = photo.fileName,
                contentType = photo.contentType,
                sizeBytes = photo.sizeBytes,
                sha256 = sha256,
                createdAt = clock(),
                updatedAt = clock()
            )
        )
    }

    suspend fun enqueueDeletePhoto(orderId: String, photoId: String): String =
        enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                type = TYPE_DELETE_PHOTO,
                payloadJson = """{"photoId":"$photoId"}""",
                createdAt = clock(),
                updatedAt = clock()
            )
        )

    /** Reset stale SENDING rows; they must be manually reconciled, never auto-resent. */
    suspend fun resetStaleSending(now: Long = clock()): Int =
        dao.resetStaleSending(cutoff = now - STALE_SENDING_MS, now = now)

    /**
     * Manual reconciliation entry point for the batch 5 UI.
     * Marks NEEDS_RECONCILIATION rows as PENDING with a marker; the worker re-reads the
     * server state before sending anything.
     */
    suspend fun reconcile(orderId: String? = null) {
        dao.requestReconciliation(orderId, clock())
        wake()
    }

    /** After a successful login, retry operations that were parked on NEEDS_AUTH. */
    suspend fun resumeAfterLogin() {
        dao.resetNeedsAuthToPending(clock())
        wake()
    }

    val counts: Flow<List<StatusCount>> get() = dao.observeCounts()
    val activeCount: Flow<Int> get() = dao.observeActiveCount()

    suspend fun deleteFile(path: String?) {
        path ?: return
        withContext(ioDispatcher) { File(path).delete() }
    }

    private suspend fun enqueue(operation: SyncOperationEntity): String {
        dao.insert(operation.copy(status = STATUS_PENDING))
        wake()
        return operation.id
    }

    companion object {
        const val STALE_SENDING_MS = 5 * 60 * 1000L
        const val RECONCILE_MARKER = "reconcile requested"
        const val WORKS_NOT_SYNCED = "works not synced"
        const val BASELINE_EXPIRED = "baseline older than 24h"
    }
}
