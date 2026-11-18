package com.example.mechanicworkplace.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mechanicworkplace.data.photo.PhotoCheckResult
import com.example.mechanicworkplace.data.photo.PhotoPrecheck
import com.example.mechanicworkplace.data.photo.PhotoRepository
import com.example.mechanicworkplace.data.photo.PhotoResolver
import com.example.mechanicworkplace.data.photo.PreparedPhoto
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoUiState(
    val photos: List<PhotoMetaDto> = emptyList(),
    val files: Map<String, File> = emptyMap(),
    val failedDownloads: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val isUploading: Boolean = false,
    val uploadingName: String? = null,
    val uploadError: String? = null,
    val deletingId: String? = null,
    val deleteError: String? = null,
    val queuedMessage: String? = null
)

class PhotoViewModel(
    private val orderId: String,
    private val repository: PhotoRepository,
    private val resolver: PhotoResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoUiState())
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    private var lastPrepared: PreparedPhoto? = null
    private val downloading = mutableSetOf<String>()

    fun load(forceRefresh: Boolean = false) {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val photos = repository.getPhotos(orderId, forceRefresh)
                _state.update { it.copy(photos = photos, isLoading = false, loadError = null) }
                photos.forEach(::download)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, loadError = t.toUserMessage()) }
            }
        }
    }

    fun download(photo: PhotoMetaDto) {
        if (_state.value.files.containsKey(photo.id)) return
        if (!downloading.add(photo.id)) return
        viewModelScope.launch {
            try {
                val file = repository.download(orderId, photo)
                _state.update {
                    it.copy(
                        files = it.files + (photo.id to file),
                        failedDownloads = it.failedDownloads - photo.id
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                _state.update { it.copy(failedDownloads = it.failedDownloads + photo.id) }
            } finally {
                downloading.remove(photo.id)
            }
        }
    }

    fun uploadFile(file: File) {
        if (_state.value.isUploading) return
        viewModelScope.launch {
            uploadPrepared(resolver.fromFile(file))
        }
    }

    fun uploadUris(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isUploading) return
        viewModelScope.launch {
            for (uri in uris) {
                val prepared = try {
                    resolver.fromUri(uri)
                } catch (t: Throwable) {
                    _state.update { it.copy(uploadError = "Не удалось прочитать выбранный файл") }
                    return@launch
                }
                if (!uploadPrepared(prepared)) return@launch
            }
        }
    }

    fun canRetryUpload(): Boolean = lastPrepared != null

    fun retryUpload() {
        val prepared = lastPrepared ?: return
        if (_state.value.isUploading) return
        viewModelScope.launch { uploadPrepared(prepared) }
    }

    fun delete(photo: PhotoMetaDto) {
        if (!photo.canDelete || _state.value.deletingId != null) return
        _state.update { it.copy(deletingId = photo.id, deleteError = null) }
        viewModelScope.launch {
            try {
                repository.enqueueDelete(orderId, photo)
                _state.update {
                    it.copy(
                        photos = it.photos.filterNot { item -> item.id == photo.id },
                        files = it.files - photo.id,
                        deletingId = null,
                        queuedMessage = "Удаление поставлено в очередь"
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update {
                    it.copy(deletingId = null, deleteError = deleteMessage(t))
                }
            }
        }
    }

    fun clearUploadError() {
        _state.update { it.copy(uploadError = null) }
    }

    fun clearQueuedMessage() {
        _state.update { it.copy(queuedMessage = null) }
    }

    /**
     * Visible for unit tests. Callers must not invoke it concurrently; the VM also guards
     * with [PhotoUiState.isUploading].
     */
    internal suspend fun uploadPrepared(prepared: PreparedPhoto): Boolean {
        if (_state.value.isUploading) return false
        if (!prepared.file.exists()) {
            _state.update { it.copy(uploadError = "Выбранный файл не найден") }
            return false
        }
        _state.update {
            it.copy(
                isUploading = true,
                uploadingName = prepared.fileName,
                uploadError = null
            )
        }
        lastPrepared = prepared
        return try {
            val existing = repository.getPhotos(orderId)
            when (val check = PhotoPrecheck.validate(prepared, existing.size)) {
                is PhotoCheckResult.Invalid -> {
                    if (check.reason != com.example.mechanicworkplace.data.photo.PhotoRejectReason.LIMIT) {
                        prepared.file.delete()
                        lastPrepared = null
                    }
                    _state.update { it.copy(uploadError = check.message) }
                    false
                }

                PhotoCheckResult.Valid -> {
                    val photo = repository.enqueueUpload(orderId, prepared)
                    lastPrepared = null
                    _state.update {
                        it.copy(
                            photos = it.photos + photo,
                            queuedMessage = "Фотография поставлена в очередь"
                        )
                    }
                    true
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _state.update { it.copy(uploadError = uploadMessage(t)) }
            false
        } finally {
            _state.update { it.copy(isUploading = false, uploadingName = null) }
        }
    }

    private fun uploadMessage(t: Throwable): String {
        val message = t.message.orEmpty()
        return when (t) {
            is OneCMobileException.Conflict -> when {
                message.contains("провед", ignoreCase = true) ->
                    "Заказ-наряд уже проведён, добавлять фотографии нельзя"
                message.contains("20") || message.contains("лимит", ignoreCase = true) ->
                    "Не более 20 фотографий на заказ-наряд"
                else -> message.ifBlank { "Конфликт при загрузке фотографии" }
            }
            is OneCMobileException.TooLarge -> "Файл больше 10 МБ"
            is OneCMobileException.UnsupportedMediaType ->
                "Поддерживаются только JPG, JPEG и PNG"
            else -> t.toUserMessage()
        }
    }

    private fun deleteMessage(t: Throwable): String = when (t) {
        is OneCMobileException.Forbidden -> "Нет прав на удаление этой фотографии"
        is OneCMobileException.Conflict -> "Заказ-наряд проведён, удалять фотографии нельзя"
        else -> t.toUserMessage()
    }
}

class PhotoViewModelFactory(
    private val orderId: String,
    private val repository: PhotoRepository,
    private val resolver: PhotoResolver
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PhotoViewModel(orderId, repository, resolver) as T
}
