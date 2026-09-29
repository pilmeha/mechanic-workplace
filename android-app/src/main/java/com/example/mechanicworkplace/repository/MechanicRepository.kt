package com.example.mechanicworkplace.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.mechanicworkplace.data.local.*
import com.example.mechanicworkplace.data.remote.ApiClient
import com.example.mechanicworkplace.data.session.SessionStore
import com.example.mechanicworkplace.model.*
import com.example.mechanicworkplace.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class MechanicRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val api: ApiClient,
    private val session: SessionStore,
    private val scheduler: SyncScheduler
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val pendingCount: Flow<Int> = db.syncQueueDao().observePendingCount()

    suspend fun isLoggedIn(): Boolean = session.isLoggedIn()

    suspend fun login(login: String, password: String): TokenResponse {
        val response = api.login(LoginRequest(login, password))
        session.save(response.accessToken, json.encodeToString(response.user))
        return response
    }

    suspend fun logout() = session.clear()

    suspend fun loadHome(): WorkOrderListDto {
        return try {
            val remote = api.workOrders()
            cacheHome(remote)
            remote
        } catch (t: Throwable) {
            val cached = db.workOrderDao().getAll()
            if (cached.isEmpty()) throw t
            val summaries = cached.map {
                WorkOrderSummaryDto(
                    id = it.id,
                    number = it.number,
                    status = it.status,
                    scheduledAt = it.scheduledAt,
                    vehicle = json.decodeFromString(it.vehicleJson)
                )
            }
            WorkOrderListDto(
                current = summaries.firstOrNull { it.status == "IN_PROGRESS" },
                upcoming = summaries.filter { it.status != "IN_PROGRESS" }
            )
        }
    }

    suspend fun loadDetails(id: String): WorkOrderDetailsDto {
        return try {
            val remote = api.workOrder(id)
            db.workOrderDao().upsertDetail(
                CachedWorkOrderDetailsEntity(id, json.encodeToString(remote))
            )
            remote
        } catch (t: Throwable) {
            db.workOrderDao().getDetail(id)?.let {
                json.decodeFromString<WorkOrderDetailsDto>(it.json)
            } ?: throw t
        }
    }

    suspend fun startOperation(workOrderId: String, operationId: String) {
        val detail = cachedDetail(workOrderId) ?: loadDetails(workOrderId)
        val now = java.time.Instant.now().toString()
        val updated = detail.copy(
            operations = detail.operations.map {
                if (it.id == operationId) it.copy(status = "IN_PROGRESS", startedAt = now) else it
            },
            status = if (detail.status == "ASSIGNED") "IN_PROGRESS" else detail.status
        )
        cacheDetail(updated)
        enqueue(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                type = SyncQueueEntity.TYPE_START_OPERATION,
                workOrderId = workOrderId,
                operationId = operationId,
                idempotencyKey = UUID.randomUUID().toString()
            )
        )
    }

    suspend fun completeOperation(workOrderId: String, operationId: String, description: String?) {
        val detail = cachedDetail(workOrderId) ?: loadDetails(workOrderId)
        val now = java.time.Instant.now().toString()
        val updated = detail.copy(
            operations = detail.operations.map {
                if (it.id == operationId) {
                    it.copy(status = "COMPLETED", completedAt = now, description = description?.takeIf(String::isNotBlank))
                } else it
            }
        )
        cacheDetail(updated)
        enqueue(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                type = SyncQueueEntity.TYPE_COMPLETE_OPERATION,
                workOrderId = workOrderId,
                operationId = operationId,
                payloadJson = json.encodeToString(CompleteOperationRequest(description)),
                idempotencyKey = UUID.randomUUID().toString()
            )
        )
    }

    suspend fun createIssue(
        workOrderId: String,
        operationId: String?,
        description: String,
        additionalWorkRequired: Boolean
    ) {
        val detail = cachedDetail(workOrderId) ?: loadDetails(workOrderId)
        val issue = IssueDto(
            id = "local-${UUID.randomUUID()}",
            description = description.trim(),
            additionalWorkRequired = additionalWorkRequired,
            operationId = operationId,
            createdAt = java.time.Instant.now().toString()
        )
        cacheDetail(detail.copy(issues = detail.issues + issue))
        enqueue(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                type = SyncQueueEntity.TYPE_CREATE_ISSUE,
                workOrderId = workOrderId,
                payloadJson = json.encodeToString(
                    CreateIssueRequest(description.trim(), additionalWorkRequired, operationId)
                ),
                idempotencyKey = UUID.randomUUID().toString()
            )
        )
    }

    suspend fun addGalleryFiles(workOrderId: String, operationId: String?, uris: List<Uri>) {
        uris.forEach { uri -> addPhoto(workOrderId, operationId, uri) }
    }

    suspend fun addCameraFile(workOrderId: String, operationId: String?, file: File) {
        enqueuePhoto(workOrderId, operationId, copyToPending(file, file.name))
        file.delete()
    }

    suspend fun enqueuePhotoFromUri(workOrderId: String, operationId: String?, uri: Uri) {
        val name = queryDisplayName(uri) ?: "photo-${UUID.randomUUID()}.jpg"
        val target = copyToPending(uri, name)
        enqueuePhoto(workOrderId, operationId, target)
    }

    fun observePendingPhotos(workOrderId: String): Flow<List<SyncQueueEntity>> =
        db.syncQueueDao().observePendingPhotos(workOrderId)

    fun observeWorkOrderPending(workOrderId: String): Flow<Int> =
        db.syncQueueDao().observeWorkOrderPendingCount(workOrderId)

    suspend fun syncQueueOnce() {
        val queue = db.syncQueueDao().nextBatch(20)
        for (item in queue) {
            processQueueItem(item)
        }
    }

    private suspend fun processQueueItem(item: SyncQueueEntity) {
        val processing = item.copy(status = SyncQueueEntity.STATUS_PROCESSING)
        db.syncQueueDao().update(processing)
        try {
            when (item.type) {
                SyncQueueEntity.TYPE_START_OPERATION -> {
                    api.startOperation(item.workOrderId, item.operationId!!, item.idempotencyKey)
                }
                SyncQueueEntity.TYPE_COMPLETE_OPERATION -> {
                    val request = json.decodeFromString<CompleteOperationRequest>(item.payloadJson ?: "{}")
                    api.completeOperation(item.workOrderId, item.operationId!!, request, item.idempotencyKey)
                }
                SyncQueueEntity.TYPE_CREATE_ISSUE -> {
                    val request = json.decodeFromString<CreateIssueRequest>(item.payloadJson!!)
                    api.createIssue(item.workOrderId, request, item.idempotencyKey)
                }
                SyncQueueEntity.TYPE_UPLOAD_ATTACHMENT -> {
                    val file = File(item.localFilePath!!)
                    if (!file.exists()) error("Локальный файл фото не найден: ${file.absolutePath}")
                    api.uploadAttachment(
                        workOrderId = item.workOrderId,
                        operationId = item.operationId,
                        file = file,
                        fileName = item.fileName ?: file.name,
                        mimeType = item.mimeType ?: "image/jpeg",
                        key = item.idempotencyKey
                    )
                    file.delete()
                }
                else -> error("Неизвестный тип очереди: ${item.type}")
            }
            db.syncQueueDao().delete(item.id)
        } catch (t: Throwable) {
            val attempts = item.attempts + 1
            val status = if (attempts >= 5) SyncQueueEntity.STATUS_FAILED else SyncQueueEntity.STATUS_PENDING
            db.syncQueueDao().update(
                processing.copy(
                    status = status,
                    attempts = attempts,
                    lastError = t.message ?: t.javaClass.simpleName
                )
            )
            if (attempts < 5) throw t
        }
    }

    private suspend fun cacheHome(data: WorkOrderListDto) {
        val summaries = buildList {
            data.current?.let { add(it) }
            addAll(data.upcoming)
        }
        db.withTransaction {
            db.workOrderDao().clear()
            db.workOrderDao().upsertAll(
                summaries.map {
                    CachedWorkOrderEntity(
                        id = it.id,
                        number = it.number,
                        status = it.status,
                        scheduledAt = it.scheduledAt,
                        vehicleJson = json.encodeToString(it.vehicle),
                        isCurrent = it.id == data.current?.id
                    )
                }
            )
        }
    }

    private suspend fun cachedDetail(id: String): WorkOrderDetailsDto? =
        db.workOrderDao().getDetail(id)?.let { json.decodeFromString(it.json) }

    private suspend fun cacheDetail(detail: WorkOrderDetailsDto) {
        db.workOrderDao().upsertDetail(
            CachedWorkOrderDetailsEntity(detail.id, json.encodeToString(detail))
        )
    }

    private suspend fun enqueue(item: SyncQueueEntity) {
        db.syncQueueDao().insert(item)
        scheduler.enqueue()
    }

    private suspend fun enqueuePhoto(workOrderId: String, operationId: String?, file: File) {
        enqueue(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                type = SyncQueueEntity.TYPE_UPLOAD_ATTACHMENT,
                workOrderId = workOrderId,
                operationId = operationId,
                localFilePath = file.absolutePath,
                fileName = file.name,
                mimeType = detectMime(file.name),
                idempotencyKey = UUID.randomUUID().toString()
            )
        )
    }

    private suspend fun addPhoto(workOrderId: String, operationId: String?, uri: Uri) {
        enqueuePhotoFromUri(workOrderId, operationId, uri)
    }

    private fun pendingDirectory(): File = File(context.filesDir, "pending-photos").apply { mkdirs() }

    private fun copyToPending(uri: Uri, originalName: String): File {
        val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(pendingDirectory(), "${UUID.randomUUID()}_$safeName")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть фото: $uri" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun copyToPending(file: File, originalName: String): File {
        val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(pendingDirectory(), "${UUID.randomUUID()}_$safeName")
        file.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        return target
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun detectMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}
