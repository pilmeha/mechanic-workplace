package com.example.mechanicworkplace.ui

import android.net.Uri
import com.example.mechanicworkplace.data.photo.PhotoRepository
import com.example.mechanicworkplace.data.photo.PhotoResolver
import com.example.mechanicworkplace.data.photo.PreparedPhoto
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `upload enqueues prepared file and keeps it for worker`() = runTest(dispatcher) {
        val file = tempFile("hello")
        val repository = FakePhotoRepository()
        val viewModel = PhotoViewModel("order-1", repository, FakePhotoResolver(), dispatcher)

        val result = viewModel.uploadPrepared(prepared(file, file.length()))
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("photo.jpg", repository.enqueued.single().fileName)
        assertTrue(file.exists())
        assertTrue(viewModel.state.value.photos.any { it.id.startsWith("queued-") })
        assertNotNull(viewModel.state.value.queuedMessage)
        assertFalse(viewModel.state.value.isUploading)
    }

    @Test
    fun `precheck rejects more than 20 photos before enqueue`() = runTest(dispatcher) {
        val file = tempFile("x")
        val repository = FakePhotoRepository().apply {
            photos += (1..20).map { photo("p$it") }
        }
        val viewModel = PhotoViewModel("order-1", repository, FakePhotoResolver(), dispatcher)

        val result = viewModel.uploadPrepared(prepared(file, file.length()))
        advanceUntilIdle()

        assertFalse(result)
        assertEquals(0, repository.enqueued.size)
        assertTrue(viewModel.state.value.uploadError?.contains("20") == true)
    }

    @Test
    fun `precheck rejects unsupported mime and size`() = runTest(dispatcher) {
        val mimeFile = tempFile("mime")
        val bigFile = tempFile("big")
        val repository = FakePhotoRepository()
        val viewModel = PhotoViewModel("order-1", repository, FakePhotoResolver(), dispatcher)

        viewModel.uploadPrepared(PreparedPhoto(mimeFile, "photo.png", "image/jpeg", mimeFile.length()))
        assertTrue(viewModel.state.value.uploadError?.contains("MIME") == true)

        viewModel.uploadPrepared(
            PreparedPhoto(bigFile, "photo.jpg", "image/jpeg", 10L * 1024 * 1024 + 1)
        )
        assertTrue(viewModel.state.value.uploadError?.contains("10 МБ") == true)
        assertEquals(0, repository.enqueued.size)
    }

    @Test
    fun `409 limit and posted messages are mapped separately`() = runTest(dispatcher) {
        val file = tempFile("x")
        val limit = PhotoViewModel(
            "order-1",
            FakePhotoRepository().apply {
                enqueueFailure = OneCMobileException.Conflict("Превышен лимит: 20 фото")
            },
            FakePhotoResolver(),
            dispatcher
        )
        limit.uploadPrepared(prepared(file))
        assertTrue(limit.state.value.uploadError?.contains("20") == true)

        val posted = PhotoViewModel(
            "order-1",
            FakePhotoRepository().apply {
                enqueueFailure = OneCMobileException.Conflict("Заказ-наряд уже проведён")
            },
            FakePhotoResolver(),
            dispatcher
        )
        posted.uploadPrepared(prepared(file))
        assertTrue(posted.state.value.uploadError?.contains("провед") == true)
    }

    @Test
    fun `413 and 415 are mapped`() = runTest(dispatcher) {
        val file = tempFile("x")
        val tooLarge = PhotoViewModel(
            "order-1",
            FakePhotoRepository().apply { enqueueFailure = OneCMobileException.TooLarge("") },
            FakePhotoResolver(),
            dispatcher
        )
        tooLarge.uploadPrepared(prepared(file))
        assertEquals("Файл больше 10 МБ", tooLarge.state.value.uploadError)

        val unsupported = PhotoViewModel(
            "order-1",
            FakePhotoRepository().apply {
                enqueueFailure = OneCMobileException.UnsupportedMediaType("")
            },
            FakePhotoResolver(),
            dispatcher
        )
        unsupported.uploadPrepared(prepared(file))
        assertEquals("Поддерживаются только JPG, JPEG и PNG", unsupported.state.value.uploadError)
    }

    @Test
    fun `delete is allowed only for canDelete photos`() = runTest(dispatcher) {
        val repository = FakePhotoRepository().apply {
            photos += photo("p1", canDelete = false)
            photos += photo("p2", canDelete = true)
        }
        val viewModel = PhotoViewModel("order-1", repository, FakePhotoResolver(), dispatcher)
        viewModel.load()
        advanceUntilIdle()

        viewModel.delete(photo("p1", canDelete = false))
        viewModel.delete(photo("p2", canDelete = true))
        advanceUntilIdle()

        assertEquals(listOf("p2"), repository.deleted.map { it.id })
        assertTrue(viewModel.state.value.photos.none { it.id == "p2" })
    }

    @Test
    fun `delete maps 403 and 409`() = runTest(dispatcher) {
        val repository = FakePhotoRepository().apply {
            photos += photo("p1")
            deleteFailure = OneCMobileException.Forbidden("")
        }
        val viewModel = PhotoViewModel("order-1", repository, FakePhotoResolver(), dispatcher)
        viewModel.load()
        advanceUntilIdle()

        viewModel.delete(photo("p1"))
        advanceUntilIdle()
        assertEquals("Нет прав на удаление этой фотографии", viewModel.state.value.deleteError)

        repository.deleteFailure = OneCMobileException.Conflict("")
        viewModel.delete(photo("p1"))
        advanceUntilIdle()
        assertEquals("Заказ-наряд проведён, удалять фотографии нельзя", viewModel.state.value.deleteError)
    }

    @Test
    fun `retry re-enqueues after failure and keeps file`() = runTest(dispatcher) {
        val file = tempFile("retry")
        val repository = FakePhotoRepository().apply {
            enqueueFailure = OneCMobileException.Network("timeout")
        }
        val viewModel = PhotoViewModel("order-1", repository, FakePhotoResolver(), dispatcher)
        viewModel.uploadPrepared(prepared(file, file.length()))
        advanceUntilIdle()
        assertTrue(file.exists())

        repository.enqueueFailure = null
        viewModel.retryUpload()
        advanceUntilIdle()

        assertEquals(2, repository.enqueueAttempts)
        assertTrue(file.exists())
    }

    private fun prepared(file: File, size: Long = 4): PreparedPhoto =
        PreparedPhoto(file, "photo.jpg", "image/jpeg", size)

    private fun tempFile(content: String): File =
        File.createTempFile("photo-test-", ".jpg").apply {
            writeText(content)
            deleteOnExit()
        }

    private fun photo(id: String, canDelete: Boolean = true): PhotoMetaDto = PhotoMetaDto(
        id = id,
        fileName = "$id.jpg",
        size = 10,
        uploadedBy = "Иван",
        uploadedAt = "2026-11-12T10:12:00",
        canDelete = canDelete
    )

    private class FakePhotoRepository : PhotoRepository {
        val photos = mutableListOf<PhotoMetaDto>()
        val enqueued = mutableListOf<PreparedPhoto>()
        val deleted = mutableListOf<PhotoMetaDto>()
        var enqueueFailure: Throwable? = null
        var deleteFailure: Throwable? = null
        var enqueueAttempts = 0
        private var sequence = 0

        override suspend fun getPhotos(orderId: String, forceRefresh: Boolean): List<PhotoMetaDto> = photos.toList()

        override suspend fun enqueueUpload(orderId: String, photo: PreparedPhoto): PhotoMetaDto {
            enqueueAttempts++
            enqueueFailure?.let { throw it }
            enqueued += photo
            return PhotoMetaDto(
                id = "queued-${++sequence}",
                fileName = photo.fileName,
                size = photo.sizeBytes,
                uploadedBy = null,
                uploadedAt = "2026-11-12T10:12:00",
                canDelete = false
            )
        }

        override suspend fun download(orderId: String, photo: PhotoMetaDto): File =
            File.createTempFile("downloaded-", ".jpg").apply { deleteOnExit() }

        override suspend fun enqueueDelete(orderId: String, photo: PhotoMetaDto) {
            deleteFailure?.let { throw it }
            deleted += photo
            photos.removeAll { it.id == photo.id }
        }
    }

    private class FakePhotoResolver : PhotoResolver {
        override suspend fun fromUri(uri: Uri): PreparedPhoto = error("not used")

        override fun fromFile(file: File): PreparedPhoto =
            PreparedPhoto(file, file.name, "image/jpeg", file.length())
    }
}
