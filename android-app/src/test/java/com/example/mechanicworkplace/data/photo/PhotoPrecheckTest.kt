package com.example.mechanicworkplace.data.photo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PhotoPrecheckTest {

    @Test
    fun `accepts jpg jpeg png with matching mime`() {
        assertValid("photo.jpg", "image/jpeg", 1024)
        assertValid("photo.jpeg", "image/jpeg", 1024)
        assertValid("photo.png", "IMAGE/PNG; charset=binary", 1024)
    }

    @Test
    fun `rejects unsupported extension`() {
        assertInvalid("photo.gif", "image/gif", 1024, PhotoRejectReason.EXTENSION)
        assertInvalid("photo", "image/jpeg", 1024, PhotoRejectReason.EXTENSION)
    }

    @Test
    fun `rejects mime that does not match extension`() {
        assertInvalid("photo.png", "image/jpeg", 1024, PhotoRejectReason.MIME)
        assertInvalid("photo.jpg", "image/png", 1024, PhotoRejectReason.MIME)
        assertInvalid("photo.jpg", "application/octet-stream", 1024, PhotoRejectReason.MIME)
    }

    @Test
    fun `rejects file larger than 10 mb`() {
        assertInvalid(
            "photo.jpg",
            "image/jpeg",
            PhotoPrecheck.MAX_BYTES + 1,
            PhotoRejectReason.TOO_LARGE
        )
    }

    @Test
    fun `rejects when order already has 20 photos`() {
        val result = PhotoPrecheck.validate(
            PreparedPhoto(File("photo.jpg"), "photo.jpg", "image/jpeg", 1024),
            existingCount = 20
        )
        assertTrue(result is PhotoCheckResult.Invalid)
        assertEquals(PhotoRejectReason.LIMIT, (result as PhotoCheckResult.Invalid).reason)
    }

    @Test
    fun `accepts 19 existing photos`() {
        val result = PhotoPrecheck.validate(
            PreparedPhoto(File("photo.jpg"), "photo.jpg", "image/jpeg", 1024),
            existingCount = 19
        )
        assertEquals(PhotoCheckResult.Valid, result)
    }

    private fun assertValid(name: String, mime: String, size: Long) {
        val result = PhotoPrecheck.validate(
            PreparedPhoto(File(name), name, mime, size),
            existingCount = 0
        )
        assertEquals(PhotoCheckResult.Valid, result)
    }

    private fun assertInvalid(
        name: String,
        mime: String,
        size: Long,
        reason: PhotoRejectReason
    ) {
        val result = PhotoPrecheck.validate(
            PreparedPhoto(File(name), name, mime, size),
            existingCount = 0
        )
        assertTrue(result is PhotoCheckResult.Invalid)
        assertEquals(reason, (result as PhotoCheckResult.Invalid).reason)
    }
}
