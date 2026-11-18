package com.example.mechanicworkplace.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.mechanicworkplace.data.photo.PhotoRepository
import com.example.mechanicworkplace.data.photo.PhotoResolver
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import com.example.mechanicworkplace.ui.PhotoUiState
import com.example.mechanicworkplace.ui.PhotoViewModel
import com.example.mechanicworkplace.ui.PhotoViewModelFactory
import com.example.mechanicworkplace.util.DateFormats
import java.io.File

/**
 * Photo grid + upload/download/delete.
 *
 * TODO(contract-clarification): the old demo expects 5 photos; the contract allows 20.
 * All traffic is JSON/base64; binary downloads carry the same Basic header via the API client.
 */
@Composable
fun PhotoSection(
    orderId: String,
    repository: PhotoRepository,
    resolver: PhotoResolver,
    canUpload: Boolean,
    refreshVersion: Int = 0
) {
    val viewModel: PhotoViewModel = viewModel(
        key = "photos-$orderId",
        factory = PhotoViewModelFactory(orderId, repository, resolver)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var cameraFile by remember { mutableStateOf<File?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> viewModel.uploadUris(uris) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraFile
        if (success && file != null) {
            viewModel.uploadFile(file)
        } else {
            file?.delete()
        }
        cameraFile = null
    }

    fun captureCamera() {
        val file = File.createTempFile("mechanic-photo-", ".jpg", context.cacheDir)
        cameraFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(orderId) {
        viewModel.load()
    }
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0) viewModel.load(forceRefresh = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Фотографии",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (canUpload) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !state.isUploading
                    ) { Text("Галерея") }
                    OutlinedButton(
                        onClick = { captureCamera() },
                        enabled = !state.isUploading
                    ) { Text("Камера") }
                }
            }
        }

        if (state.isUploading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Загрузка: ${state.uploadingName.orEmpty()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.uploadError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            if (viewModel.canRetryUpload()) {
                TextButton(onClick = viewModel::retryUpload) { Text("Повторить загрузку") }
            }
        }
        state.deleteError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        state.queuedMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        state.loadError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::load) { Text("Повторить") }
        }

        when {
            state.isLoading && state.photos.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.photos.isEmpty() -> Text(
                "Фотографий пока нет",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> PhotoGrid(
                state = state,
                canModify = canUpload,
                onDownload = viewModel::download,
                onDelete = viewModel::delete
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    state: PhotoUiState,
    canModify: Boolean,
    onDownload: (PhotoMetaDto) -> Unit,
    onDelete: (PhotoMetaDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.photos.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { photo ->
                    PhotoCell(
                        photo = photo,
                        file = state.files[photo.id],
                        failed = state.failedDownloads.contains(photo.id),
                        canModify = canModify,
                        onDownload = { onDownload(photo) },
                        onDelete = { onDelete(photo) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: PhotoMetaDto,
    file: File?,
    failed: Boolean,
    canModify: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            when {
                photo.id.startsWith("queued-") -> Text(
                    "В очереди",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                file != null -> AsyncImage(
                    model = file,
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                failed -> Text(
                    "Ошибка загрузки",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(onClick = onDownload),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                        strokeWidth = 2.dp
                    )
                    LaunchedEffect(photo.id) { onDownload() }
                }
            }

            if (photo.canDelete && canModify) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить фотографию",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Text(
            photo.fileName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(
                photo.uploadedBy,
                DateFormats.display(photo.uploadedAt).takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
