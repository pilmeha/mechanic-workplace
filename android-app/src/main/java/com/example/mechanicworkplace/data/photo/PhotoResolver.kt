package com.example.mechanicworkplace.data.photo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Converts a gallery Uri or a camera file into a file + metadata for precheck/upload. */
interface PhotoResolver {
    suspend fun fromUri(uri: Uri): PreparedPhoto
    fun fromFile(file: File): PreparedPhoto
}

class AndroidPhotoResolver(
    private val context: Context,
    private val workDir: File
) : PhotoResolver {

    override suspend fun fromUri(uri: Uri): PreparedPhoto = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
            ?: "photo-${UUID.randomUUID()}.jpg"
        val mimeType = normalizeMime(
            rawMime = context.contentResolver.getType(uri),
            fileName = displayName
        )
        val target = File(workDir.apply { mkdirs() }, "${UUID.randomUUID()}_${sanitize(displayName)}")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Не удалось открыть файл: $uri" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        PreparedPhoto(
            file = target,
            fileName = displayName,
            contentType = mimeType,
            sizeBytes = target.length()
        )
    }

    override fun fromFile(file: File): PreparedPhoto = PreparedPhoto(
        file = file,
        fileName = file.name,
        contentType = guessMime(file.name),
        sizeBytes = file.length()
    )

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "photo.jpg" }

    private fun normalizeMime(rawMime: String?, fileName: String): String {
        val mime = rawMime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return when (mime) {
            "image/jpg" -> "image/jpeg"
            "image/jpeg", "image/png" -> mime
            else -> guessMime(fileName)
        }
    }

    private fun guessMime(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
}
