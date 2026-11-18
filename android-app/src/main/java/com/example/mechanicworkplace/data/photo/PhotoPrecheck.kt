package com.example.mechanicworkplace.data.photo

import java.io.File

/** A local file plus the metadata sent to POST /orders/{id}/photos. */
data class PreparedPhoto(
    val file: File,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long
)

enum class PhotoRejectReason {
    EXTENSION,
    MIME,
    TOO_LARGE,
    LIMIT
}

sealed interface PhotoCheckResult {
    data object Valid : PhotoCheckResult
    data class Invalid(
        val reason: PhotoRejectReason,
        val message: String
    ) : PhotoCheckResult
}

/**
 * Contract checks before the network call:
 * - jpg/jpeg/png;
 * - contentType must match the extension (image/png for png, image/jpeg for jpg/jpeg);
 * - at most 10 MB;
 * - at most 20 photos per order (server also enforces this with 409).
 */
object PhotoPrecheck {
    const val MAX_BYTES: Long = 10L * 1024 * 1024
    const val MAX_PHOTOS: Int = 20

    private val allowedExtensions = setOf("jpg", "jpeg", "png")

    fun validate(photo: PreparedPhoto, existingCount: Int): PhotoCheckResult {
        val extension = photo.fileName.substringAfterLast('.', "").lowercase()
        if (extension !in allowedExtensions) {
            return PhotoCheckResult.Invalid(
                PhotoRejectReason.EXTENSION,
                "Поддерживаются только JPG, JPEG и PNG"
            )
        }

        val expectedMime = if (extension == "png") "image/png" else "image/jpeg"
        val actualMime = photo.contentType.substringBefore(';').trim().lowercase()
        if (actualMime != expectedMime) {
            return PhotoCheckResult.Invalid(
                PhotoRejectReason.MIME,
                "MIME-тип не соответствует расширению файла"
            )
        }

        if (photo.sizeBytes > MAX_BYTES) {
            return PhotoCheckResult.Invalid(
                PhotoRejectReason.TOO_LARGE,
                "Файл больше 10 МБ"
            )
        }

        if (existingCount >= MAX_PHOTOS) {
            return PhotoCheckResult.Invalid(
                PhotoRejectReason.LIMIT,
                "Не более 20 фотографий на заказ-наряд"
            )
        }

        return PhotoCheckResult.Valid
    }
}
