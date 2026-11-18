package com.example.mechanicworkplace.data.photo

import com.example.mechanicworkplace.data.local.AppDatabase
import com.example.mechanicworkplace.data.local.CachedPhotoEntity
import com.example.mechanicworkplace.data.remote.OrdersDataSource
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import com.example.mechanicworkplace.sync.SyncQueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface PhotoRepository {
    suspend fun getPhotos(orderId: String, forceRefresh: Boolean = false): List<PhotoMetaDto>
    suspend fun enqueueUpload(orderId: String, photo: PreparedPhoto): PhotoMetaDto
    suspend fun download(orderId: String, photo: PhotoMetaDto): File
    suspend fun enqueueDelete(orderId: String, photo: PhotoMetaDto)
}

/**
 * Metadata reads are Room-first with network refresh; writes only enqueue (stage 6).
 * Binary downloads stay on the API client (Basic header, no envelope).
 */
class DefaultPhotoRepository(
    private val dataSource: OrdersDataSource,
    private val db: AppDatabase,
    private val syncQueue: SyncQueueRepository,
    private val cacheDir: File
) : PhotoRepository {

    private val photoDao = db.photoCacheDao()

    override suspend fun getPhotos(orderId: String, forceRefresh: Boolean): List<PhotoMetaDto> {
        val cached = photoDao.getForOrder(orderId)
        if (!forceRefresh && cached.isNotEmpty()) return cached.map { it.toDto() }
        return try {
            val photos = dataSource.getPhotos(orderId)
            photoDao.upsertAll(photos.map { it.toEntity(orderId) })
            photos
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (cached.isEmpty()) throw t
            cached.map { it.toDto() }
        }
    }

    override suspend fun enqueueUpload(orderId: String, photo: PreparedPhoto): PhotoMetaDto {
        syncQueue.enqueueUploadPhoto(orderId, photo)
        // Local optimistic metadata; the real id arrives when the worker uploads.
        return PhotoMetaDto(
            id = "queued-${UUID.randomUUID()}",
            fileName = photo.fileName,
            size = photo.sizeBytes,
            uploadedBy = null,
            uploadedAt = java.time.OffsetDateTime.now().toString(),
            canDelete = false
        )
    }

    override suspend fun download(orderId: String, photo: PhotoMetaDto): File =
        withContext(Dispatchers.IO) {
            val directory = File(cacheDir, sanitize(orderId)).apply { mkdirs() }
            val extension = photo.fileName
                .substringAfterLast('.', "jpg")
                .lowercase()
                .takeIf { it in ALLOWED_EXTENSIONS } ?: "jpg"
            val target = File(directory, "${sanitize(photo.id)}.$extension")
            if (target.exists() && target.length() > 0) return@withContext target
            val bytes = dataSource.getPhotoBinary(orderId, photo.id)
            target.writeBytes(bytes)
            target
        }

    override suspend fun enqueueDelete(orderId: String, photo: PhotoMetaDto) {
        syncQueue.enqueueDeletePhoto(orderId, photo.id)
    }

    private fun CachedPhotoEntity.toDto() = PhotoMetaDto(
        id = photoId,
        fileName = fileName,
        size = size,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
        canDelete = canDelete
    )

    private fun PhotoMetaDto.toEntity(orderId: String) = CachedPhotoEntity(
        orderId = orderId,
        photoId = id,
        fileName = fileName,
        size = size,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
        canDelete = canDelete,
        updatedAt = System.currentTimeMillis()
    )

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private companion object {
        val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }
}
