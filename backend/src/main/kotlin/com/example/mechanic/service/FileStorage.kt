package com.example.mechanic.service

import io.ktor.http.content.PartData
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

class FileStorage(private val root: Path) {
    init {
        Files.createDirectories(root)
    }

    suspend fun save(part: PartData.FileItem, workOrderId: UUID, maxBytes: Long): SavedFile {
        val originalName = sanitize(part.originalFileName ?: "photo.jpg")
        val extension = originalName.substringAfterLast('.', "jpg").lowercase()
        val allowed = setOf("jpg", "jpeg", "png", "webp")
        if (extension !in allowed) {
            throw ApiException(io.ktor.http.HttpStatusCode.BadRequest, "INVALID_FILE_TYPE", "Only JPG, JPEG, PNG and WEBP images are supported")
        }
        val id = UUID.randomUUID()
        val directory = root.resolve(workOrderId.toString())
        withContext(Dispatchers.IO) { Files.createDirectories(directory) }
        val target = directory.resolve("$id.$extension")
        withContext(Dispatchers.IO) {
            part.provider().copyAndClose(target.toFile().writeChannel())
        }
        val size = withContext(Dispatchers.IO) { Files.size(target) }
        if (size > maxBytes) {
            withContext(Dispatchers.IO) { Files.deleteIfExists(target) }
            throw ApiException(io.ktor.http.HttpStatusCode.PayloadTooLarge, "FILE_TOO_LARGE", "Image exceeds the maximum allowed size")
        }
        return SavedFile(
            id = id,
            originalName = originalName,
            relativePath = root.relativize(target).toString().replace('\\', '/'),
            absolutePath = target,
            sizeBytes = size
        )
    }

    fun delete(relativePath: String) {
        val target = root.resolve(relativePath).normalize()
        if (!target.startsWith(root.normalize())) return
        Files.deleteIfExists(target)
    }

    fun resolve(relativePath: String): Path {
        val target = root.resolve(relativePath).normalize()
        require(target.startsWith(root.normalize())) { "Invalid storage path" }
        return target
    }

    private fun sanitize(name: String): String = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(120)
        .ifBlank { "photo.jpg" }
}

data class SavedFile(
    val id: UUID,
    val originalName: String,
    val relativePath: String,
    val absolutePath: Path,
    val sizeBytes: Long
)
