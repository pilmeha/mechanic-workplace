package com.example.mechanicworkplace.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import java.math.BigDecimal

@Composable
fun AddDefectDialog(
    defect: DefectCatalogItemDto,
    isSaving: Boolean,
    needsConfirmation: Boolean,
    error: String?,
    onConfirm: (content: String?, normHour: BigDecimal?) -> Unit,
    onDismiss: () -> Unit
) {
    var content by rememberSaveable(defect.id) { mutableStateOf("") }
    var normHourText by rememberSaveable(defect.id) {
        mutableStateOf(defect.normHour?.toPlainString().orEmpty())
    }
    var inputError by rememberSaveable(defect.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Неисправность: ${defect.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Описание (необязательно)") },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = normHourText,
                    onValueChange = { normHourText = it },
                    label = { Text("Нормочас (необязательно)") },
                    enabled = !isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                (inputError ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && !needsConfirmation,
                onClick = {
                    val raw = normHourText.trim()
                    val parsed = if (raw.isEmpty()) null else runCatching { BigDecimal(raw.replace(',', '.')) }.getOrNull()
                    if (raw.isNotEmpty() && parsed == null) {
                        inputError = "Нормочас должен быть числом"
                    } else {
                        inputError = null
                        onConfirm(content.trim().takeIf { it.isNotEmpty() }, parsed)
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Добавить")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Отмена") }
        }
    )
}
