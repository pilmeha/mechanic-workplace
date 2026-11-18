package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.CachedOrderDetailsEntity
import com.example.mechanicworkplace.data.local.CachedPhotoEntity
import com.example.mechanicworkplace.data.local.OrderCacheDao
import com.example.mechanicworkplace.data.local.PhotoCacheDao
import com.example.mechanicworkplace.data.local.SyncOperationDao
import com.example.mechanicworkplace.data.local.SyncOperationEntity
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_DONE
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_FAILED
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_NEEDS_AUTH
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_NEEDS_RECONCILIATION
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_PENDING
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.STATUS_SENDING
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_ADD_DEFECT
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_DELETE_PHOTO
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_POST_ORDER
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_SAVE_WORKS
import com.example.mechanicworkplace.data.local.SyncOperationEntity.Companion.TYPE_UPLOAD_PHOTO
import com.example.mechanicworkplace.data.remote.OrdersDataSource
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.data.remote.onecmobile.UploadPhotoRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64

sealed interface QueueRunResult {
    data object Idle : QueueRunResult
    data class Future(val nextAt: Long) : QueueRunResult
    data object Paused : QueueRunResult
}

object WorkerPolicy {
    fun shouldRetry(hasEligibleNow: Boolean): Boolean = hasEligibleNow
}

/**
 * One-at-a-time queue processor. All operations for different orders are serialized;
 * a POST_ORDER is never executed while the same order has unfinished SAVE_WORKS.
 */
class SyncQueueProcessor(
    private val dao: SyncOperationDao,
    private val orderCache: OrderCacheDao,
    private val photoCache: PhotoCacheDao,
    private val dataSource: OrdersDataSource,
    private val isPaused: () -> Boolean = { false },
    private val pauseAuth: suspend () -> Unit = {},
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun run(): QueueRunResult {
        dao.resetStaleSending(now() - SyncQueueRepository.STALE_SENDING_MS, now())
        while (true) {
            if (isPaused()) return QueueRunResult.Paused
            val candidates = dao.eligible(now(), 10)
            if (candidates.isEmpty()) {
                val next = dao.nextPendingAt()
                return if (next != null && next > now()) {
                    QueueRunResult.Future(next)
                } else {
                    QueueRunResult.Idle
                }
            }

            var progressed = false
            for (candidate in candidates) {
                if (candidate.type == TYPE_POST_ORDER &&
                    dao.unfinishedWorksCount(candidate.orderId) > 0
                ) {
                    markNeeds(candidate.id, SyncQueueRepository.WORKS_NOT_SYNCED)
                    progressed = true
                    continue
                }
                val operation = claim(candidate.id) ?: continue
                process(operation)
                progressed = true
                break
            }
            if (!progressed) return QueueRunResult.Idle
        }
    }

    suspend fun hasEligibleNow(): Boolean = dao.eligible(now(), 1).isNotEmpty()

    private suspend fun claim(id: String): SyncOperationEntity? {
        val current = dao.getById(id) ?: return null
        if (current.status != STATUS_PENDING) return null
        val claimed = current.copy(
            status = STATUS_SENDING,
            startedAt = now(),
            attempts = current.attempts + 1,
            updatedAt = now()
        )
        dao.update(claimed)
        return claimed
    }

    private suspend fun process(operation: SyncOperationEntity) {
        try {
            if (operation.lastError == SyncQueueRepository.RECONCILE_MARKER) {
                if (ReconciliationPolicy.baselineExpired(operation, now())) {
                    markNeeds(operation.id, SyncQueueRepository.BASELINE_EXPIRED)
                    return
                }
                when (val decision = reconcile(operation)) {
                    SyncDecision.DONE -> markDone(operation.id)
                    SyncDecision.FAILED -> markFailed(operation.id, "reconcile failed")
                    SyncDecision.NEEDS_RECONCILIATION ->
                        markNeeds(operation.id, "reconcile requested")
                    SyncDecision.RETRY -> Unit // proceed with the actual send
                }
            }
            send(operation)
        } catch (t: CancellationException) {
            throw t
        } catch (t: OneCMobileException.Unauthorized) {
            markNeedsAuth(operation.id)
            runCatching { pauseAuth() }
        } catch (t: OneCMobileException.Validation) {
            if (operation.type == TYPE_POST_ORDER) handlePostRejection(operation, t) else
                markFailed(operation.id, t.message ?: "validation")
        } catch (t: OneCMobileException.Conflict) {
            when (operation.type) {
                TYPE_POST_ORDER -> handlePostRejection(operation, t)
                TYPE_UPLOAD_PHOTO -> handleUploadConflict(operation, t)
                else -> markFailed(operation.id, t.message ?: "conflict")
            }
        } catch (t: OneCMobileException.Forbidden) {
            markFailed(operation.id, t.message ?: "forbidden")
        } catch (t: OneCMobileException.NotFound) {
            markFailed(operation.id, t.message ?: "not found")
        } catch (t: OneCMobileException.TooLarge) {
            markFailed(operation.id, "file too large")
        } catch (t: OneCMobileException.UnsupportedMediaType) {
            markFailed(operation.id, "unsupported media")
        } catch (t: Throwable) {
            handleUnknown(operation, t)
        }
    }

    private suspend fun send(operation: SyncOperationEntity) {
        when (operation.type) {
            TYPE_SAVE_WORKS -> {
                val request = json.decodeFromString(
                    SaveWorksRequest.serializer(),
                    operation.payloadJson ?: error("missing works payload")
                )
                val card = dataSource.saveWorks(operation.orderId, request)
                cacheDetails(card)
                markDone(operation.id)
            }

            TYPE_POST_ORDER -> {
                val result = dataSource.postOrder(operation.orderId)
                val card = runCatching { dataSource.getOrder(operation.orderId) }.getOrNull()
                if (card != null) {
                    cacheDetails(card.copy(posted = true, state = result.state))
                }
                markDone(operation.id)
            }

            TYPE_ADD_DEFECT -> {
                val request = json.decodeFromString(
                    AddDefectRequest.serializer(),
                    operation.payloadJson ?: error("missing defect payload")
                )
                val card = dataSource.addDefect(operation.orderId, request)
                cacheDetails(card)
                markDone(operation.id)
            }

            TYPE_UPLOAD_PHOTO -> {
                val path = operation.localFilePath ?: error("missing photo path")
                var bytes: ByteArray? = withContext(ioDispatcher) { File(path).readBytes() }
                val request = UploadPhotoRequest(
                    fileName = operation.fileName ?: "photo.jpg",
                    contentType = operation.contentType ?: "image/jpeg",
                    contentBase64 = Base64.getEncoder().encodeToString(requireNotNull(bytes))
                )
                bytes = null
                dataSource.uploadPhoto(operation.orderId, request)
                withContext(ioDispatcher) { File(path).delete() }
                cachePhotos(operation.orderId)
                markDone(operation.id)
            }

            TYPE_DELETE_PHOTO -> {
                val photoId = json.parseToJsonElement(operation.payloadJson ?: "{}")
                    .jsonObject["photoId"]?.jsonPrimitive?.content
                    ?: error("missing photoId")
                dataSource.deletePhoto(operation.orderId, photoId)
                photoCache.delete(operation.orderId, photoId)
                markDone(operation.id)
            }

            else -> markFailed(operation.id, "unknown operation type")
        }
    }

    private suspend fun handleUnknown(operation: SyncOperationEntity, error: Throwable) {
        if (ReconciliationPolicy.baselineExpired(operation, now())) {
            markNeeds(operation.id, SyncQueueRepository.BASELINE_EXPIRED)
            return
        }
        val decision = reconcile(operation)
        when (decision) {
            SyncDecision.DONE -> markDone(operation.id)
            SyncDecision.FAILED -> markFailed(operation.id, error.message ?: "failed")
            SyncDecision.NEEDS_RECONCILIATION ->
                markNeeds(operation.id, error.message ?: "needs reconciliation")
            SyncDecision.RETRY -> scheduleRetry(operation, error.message ?: "unknown outcome")
        }
    }

    private suspend fun reconcile(operation: SyncOperationEntity): SyncDecision =
        when (operation.type) {
            TYPE_POST_ORDER -> {
                val card = runCatching { dataSource.getOrder(operation.orderId) }.getOrNull()
                val decision = ReconciliationPolicy.postOrder(card?.posted)
                if (decision == SyncDecision.DONE && card != null) cacheDetails(card)
                decision
            }

            TYPE_SAVE_WORKS -> {
                val request = runCatching {
                    json.decodeFromString(
                        SaveWorksRequest.serializer(),
                        operation.payloadJson ?: error("missing payload")
                    )
                }.getOrNull()
                if (request == null) {
                    SyncDecision.NEEDS_RECONCILIATION
                } else {
                    val card = runCatching { dataSource.getOrder(operation.orderId) }.getOrNull()
                    val decision = ReconciliationPolicy.saveWorks(request, card)
                    if (decision == SyncDecision.DONE && card != null) cacheDetails(card)
                    decision
                }
            }

            TYPE_ADD_DEFECT -> {
                val request = runCatching {
                    json.decodeFromString(
                        AddDefectRequest.serializer(),
                        operation.payloadJson ?: error("missing payload")
                    )
                }.getOrNull()
                val beforeCount = runCatching {
                    json.parseToJsonElement(operation.baselineJson ?: "{}")
                        .jsonObject["beforeCount"]?.jsonPrimitive?.int
                }.getOrNull()
                if (request == null || beforeCount == null) {
                    SyncDecision.NEEDS_RECONCILIATION
                } else {
                    val card = runCatching { dataSource.getOrder(operation.orderId) }.getOrNull()
                    val decision = ReconciliationPolicy.addDefect(request, beforeCount, card)
                    if (decision == SyncDecision.DONE && card != null) cacheDetails(card)
                    decision
                }
            }

            TYPE_DELETE_PHOTO -> {
                val photoId = runCatching {
                    json.parseToJsonElement(operation.payloadJson ?: "{}")
                        .jsonObject["photoId"]?.jsonPrimitive?.content
                }.getOrNull()
                val photos = runCatching { dataSource.getPhotos(operation.orderId) }.getOrNull()
                if (photoId == null || photos == null) {
                    SyncDecision.NEEDS_RECONCILIATION
                } else {
                    ReconciliationPolicy.deletePhoto(photos.any { it.id == photoId })
                }
            }

            TYPE_UPLOAD_PHOTO -> ReconciliationPolicy.uploadPhotoUnknownOutcome()
            else -> SyncDecision.NEEDS_RECONCILIATION
        }

    private suspend fun handlePostRejection(
        operation: SyncOperationEntity,
        error: OneCMobileException
    ) {
        val card = runCatching { dataSource.getOrder(operation.orderId) }.getOrNull()
        when {
            card?.posted == true -> {
                cacheDetails(card)
                markDone(operation.id)
            }
            card == null -> markNeeds(operation.id, error.message ?: "post unknown")
            else -> markFailed(operation.id, error.message ?: "post rejected")
        }
    }

    private suspend fun handleUploadConflict(
        operation: SyncOperationEntity,
        error: OneCMobileException.Conflict
    ) {
        val photos = runCatching { dataSource.getPhotos(operation.orderId) }.getOrNull()
        val card = runCatching { dataSource.getOrder(operation.orderId) }.getOrNull()
        val message = error.message.orEmpty()
        when {
            card?.posted == true -> markFailed(operation.id, "order posted")
            photos != null && photos.size >= 20 -> markFailed(operation.id, "photo limit")
            message.contains("провед", true) -> markFailed(operation.id, message)
            message.contains("20") || message.contains("лимит", true) ->
                markFailed(operation.id, message)
            else -> scheduleRetry(operation, message.ifBlank { "photo conflict" })
        }
    }

    private suspend fun scheduleRetry(operation: SyncOperationEntity, message: String) {
        if (BackoffPolicy.shouldFail(operation.attempts)) {
            markFailed(operation.id, message)
            return
        }
        val delay = BackoffPolicy.delayMillis(operation.attempts)
        dao.update(
            operation.copy(
                status = STATUS_PENDING,
                startedAt = null,
                lastError = message,
                nextAttemptAt = now() + delay,
                updatedAt = now()
            )
        )
    }

    private suspend fun markDone(id: String) {
        dao.getById(id)?.let {
            dao.update(
                it.copy(
                    status = STATUS_DONE,
                    startedAt = null,
                    lastError = null,
                    updatedAt = now()
                )
            )
        }
    }

    private suspend fun markFailed(id: String, message: String) {
        dao.getById(id)?.let {
            dao.update(
                it.copy(
                    status = STATUS_FAILED,
                    startedAt = null,
                    lastError = message,
                    updatedAt = now()
                )
            )
            if (it.type == TYPE_UPLOAD_PHOTO) {
                it.localFilePath?.let { path ->
                    withContext(ioDispatcher) { File(path).delete() }
                }
            }
        }
    }

    private suspend fun markNeeds(id: String, message: String) {
        dao.getById(id)?.let {
            dao.update(
                it.copy(
                    status = STATUS_NEEDS_RECONCILIATION,
                    startedAt = null,
                    lastError = message,
                    updatedAt = now()
                )
            )
        }
    }

    private suspend fun markNeedsAuth(id: String) {
        dao.getById(id)?.let {
            dao.update(
                it.copy(
                    status = STATUS_NEEDS_AUTH,
                    startedAt = null,
                    lastError = "unauthorized",
                    updatedAt = now()
                )
            )
        }
    }

    private suspend fun cacheDetails(card: OrderCardDto) {
        orderCache.upsertDetails(
            CachedOrderDetailsEntity(
                id = card.id,
                posted = card.posted,
                json = json.encodeToString(OrderCardDto.serializer(), card),
                updatedAt = now()
            )
        )
    }

    private suspend fun cachePhotos(orderId: String) {
        val photos = runCatching { dataSource.getPhotos(orderId) }.getOrNull() ?: return
        photoCache.upsertAll(photos.map { it.toEntity(orderId) })
    }

    private fun PhotoMetaDto.toEntity(orderId: String) = CachedPhotoEntity(
        orderId = orderId,
        photoId = id,
        fileName = fileName,
        size = size,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
        canDelete = canDelete,
        updatedAt = now()
    )

    private fun now(): Long = clock()
}
