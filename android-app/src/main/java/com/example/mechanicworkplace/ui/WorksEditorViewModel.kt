package com.example.mechanicworkplace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mechanicworkplace.data.remote.onecmobile.SaveExecutorDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorkRowDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.data.remote.onecmobile.WorkExecutorDto
import com.example.mechanicworkplace.repository.OrdersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class WorksEditorRow(
    val lineId: String?,
    val workId: String,
    val name: String,
    val quantityText: String,
    val priceText: String,
    val note: String,
    val executors: List<WorkExecutorDto>
)

data class WorksEditorUiState(
    val orderId: String? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val isPosted: Boolean = false,
    val rows: List<WorksEditorRow> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedVersion: Int = 0
)

/**
 * Batch 2 editor: existing works only.
 *
 * TODO(workId catalog): no add-row support until GET /works is confirmed.
 * TODO(employee catalog): executors are read-only and returned verbatim.
 * TODO(stage 6): saveWorks is still sent directly; move it to SyncQueue.
 */
class WorksEditorViewModel(
    private val repository: OrdersRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorksEditorUiState())
    val state: StateFlow<WorksEditorUiState> = _state.asStateFlow()

    private var loadToken = 0

    fun load(orderId: String) {
        val token = ++loadToken
        _state.update {
            it.copy(orderId = orderId, isLoading = true, loadError = null, saveError = null)
        }
        viewModelScope.launch {
            try {
                val result = repository.getOrder(orderId)
                if (token != loadToken) return@launch
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPosted = result.card.posted,
                        rows = result.card.works.map { work ->
                            WorksEditorRow(
                                lineId = work.lineId,
                                workId = work.work.id,
                                name = work.work.name,
                                quantityText = work.quantity?.toPlainString().orEmpty(),
                                priceText = work.price?.toPlainString().orEmpty(),
                                note = work.note.orEmpty(),
                                executors = work.executors
                            )
                        }
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (token != loadToken) return@launch
                _state.update {
                    it.copy(isLoading = false, loadError = t.toUserMessage())
                }
            }
        }
    }

    fun updateQuantity(lineId: String?, value: String) = updateRow(lineId) {
        it.copy(quantityText = value)
    }

    fun updatePrice(lineId: String?, value: String) = updateRow(lineId) {
        it.copy(priceText = value)
    }

    fun updateNote(lineId: String?, value: String) = updateRow(lineId) {
        it.copy(note = value)
    }

    fun removeRow(lineId: String?) {
        _state.update { current ->
            current.copy(rows = current.rows.filterNot { it.lineId == lineId })
        }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return
        if (current.isPosted) {
            _state.update { it.copy(saveError = "Заказ-наряд проведён, редактирование недоступно") }
            return
        }
        val orderId = current.orderId ?: return
        if (current.rows.any { it.lineId.isNullOrBlank() || it.workId.isBlank() }) {
            _state.update {
                it.copy(saveError = "Строка без lineId/workId не может быть сохранена")
            }
            return
        }

        val requestRows = ArrayList<SaveWorkRowDto>(current.rows.size)
        for (row in current.rows) {
            val quantity = try {
                parseDecimalOrThrow(row.quantityText)
            } catch (t: NumberFormatException) {
                return fail("Количество должно быть числом")
            }
            val price = try {
                parseDecimalOrThrow(row.priceText)
            } catch (t: NumberFormatException) {
                return fail("Цена должна быть числом")
            }
            requestRows += SaveWorkRowDto(
                lineId = row.lineId,
                workId = row.workId,
                quantity = quantity,
                price = price,
                note = row.note.trim().takeIf { it.isNotEmpty() },
                executors = row.executors.map { executor ->
                    SaveExecutorDto(
                        employeeId = executor.employeeId,
                        percent = executor.percent
                    )
                }
            )
        }

        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                // TODO(stage 6): enqueue SAVE_WORKS instead of direct send.
                val card = repository.saveWorks(
                    orderId,
                    SaveWorksRequest(works = requestRows)
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        isPosted = card.posted,
                        savedVersion = it.savedVersion + 1
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isSaving = false, saveError = t.toUserMessage())
                }
            }
        }
    }

    /** Lets the screen close once without re-closing on the next open of the same ViewModel. */
    fun consumeSaved() {
        _state.update { it.copy(savedVersion = 0) }
    }

    private fun updateRow(lineId: String?, block: (WorksEditorRow) -> WorksEditorRow) {
        val index = _state.value.rows.indexOfFirst { it.lineId == lineId }
        if (index < 0) return
        _state.update { current ->
            current.copy(
                rows = current.rows.toMutableList().apply { set(index, block(get(index))) },
                saveError = null
            )
        }
    }

    private fun fail(message: String): Unit {
        _state.update { it.copy(saveError = message) }
    }

    private fun parseDecimalOrThrow(raw: String): BigDecimal? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        return BigDecimal(value.replace(',', '.'))
    }
}

class WorksEditorViewModelFactory(
    private val repository: OrdersRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WorksEditorViewModel(repository) as T
}
