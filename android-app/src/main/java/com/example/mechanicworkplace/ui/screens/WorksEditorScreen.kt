package com.example.mechanicworkplace.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mechanicworkplace.repository.OrdersRepository
import com.example.mechanicworkplace.ui.WorksEditorUiState
import com.example.mechanicworkplace.ui.WorksEditorViewModel
import com.example.mechanicworkplace.ui.WorksEditorViewModelFactory

/**
 * Batch 2 editor: only existing rows can be changed/removed.
 *
 * TODO(workId catalog): no "add row" button until GET /works is confirmed.
 * TODO(employee catalog): executors are read-only and sent back verbatim
 * (employeeId + percent, without shopId).
 * TODO(stage 6): save is sent directly; move PUT to SyncQueue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksEditorScreen(
    orderId: String,
    repository: OrdersRepository,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel: WorksEditorViewModel = viewModel(
        key = "works-editor-$orderId",
        factory = WorksEditorViewModelFactory(repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(orderId) {
        viewModel.load(orderId)
    }
    LaunchedEffect(state.savedVersion) {
        if (state.savedVersion > 0) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Работы") },
                navigationIcon = {
                    TextButton(onClick = onCancel, enabled = !state.isSaving) {
                        Text("Отмена")
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (!state.isPosted) {
                        TextButton(
                            enabled = !state.isLoading &&
                                state.rows.all { !it.lineId.isNullOrBlank() },
                            onClick = viewModel::save
                        ) { Text("Сохранить") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> Loading()
                state.loadError != null -> ErrorState(state.loadError.orEmpty()) {
                    viewModel.load(orderId)
                }
                state.isPosted -> Banner(
                    "Заказ-наряд проведён. Редактирование недоступно."
                )
                else -> EditorContent(
                    state = state,
                    onQuantity = viewModel::updateQuantity,
                    onPrice = viewModel::updatePrice,
                    onNote = viewModel::updateNote,
                    onDelete = viewModel::removeRow,
                    onSave = viewModel::save
                )
            }
        }
    }
}

@Composable
private fun EditorContent(
    state: WorksEditorUiState,
    onQuantity: (String?, String) -> Unit,
    onPrice: (String?, String) -> Unit,
    onNote: (String?, String) -> Unit,
    onDelete: (String?) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        state.saveError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (state.rows.isEmpty()) {
            Text(
                "В заказ-наряде нет работ.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.rows.any { it.lineId.isNullOrBlank() }) {
            Text(
                "Есть строка без lineId. Сохранение недоступно, чтобы не потерять связь с исполнителями.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.rows, key = { it.lineId ?: it.workId }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(row.name, fontWeight = FontWeight.SemiBold)
                            IconButton(
                                enabled = !state.isSaving,
                                onClick = { onDelete(row.lineId) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить строку")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.quantityText,
                                onValueChange = { onQuantity(row.lineId, it) },
                                label = { Text("Кол-во") },
                                singleLine = true,
                                enabled = !state.isSaving,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.priceText,
                                onValueChange = { onPrice(row.lineId, it) },
                                label = { Text("Цена") },
                                singleLine = true,
                                enabled = !state.isSaving,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = row.note,
                            onValueChange = { onNote(row.lineId, it) },
                            label = { Text("Примечание") },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (row.executors.isNotEmpty()) {
                            Text(
                                "Исполнители: " + row.executors.joinToString(" · ") { executor ->
                                    listOfNotNull(
                                        executor.name,
                                        executor.shopName,
                                        executor.percent?.let { "$it%" }
                                    ).joinToString(" ")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Добавление новых строк появится после согласования справочника работ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving && !state.isLoading && !state.isPosted &&
                state.rows.all { !it.lineId.isNullOrBlank() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Сохранить работы")
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
