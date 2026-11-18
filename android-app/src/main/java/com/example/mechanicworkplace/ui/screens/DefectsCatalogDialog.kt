package com.example.mechanicworkplace.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.ui.OrderDetailsUiState

@Composable
fun DefectsCatalogDialog(
    state: OrderDetailsUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelect: (DefectCatalogItemDto) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Справочник неисправностей") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.defectQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Поиск по названию или коду") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    state.isDefectsLoading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    state.defectError != null -> Column(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.defectError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Повторить") }
                    }

                    state.defectItems.isEmpty() -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ничего не найдено")
                    }

                    else -> LazyColumn(Modifier.height(320.dp)) {
                        items(state.defectItems, key = { it.id }) { defect ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(defect) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(defect.name)
                                val details = buildList {
                                    defect.code?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    defect.type?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    defect.normHour?.let { add("${it.toPlainString()} н/ч") }
                                }
                                if (details.isNotEmpty()) {
                                    Text(
                                        details.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
