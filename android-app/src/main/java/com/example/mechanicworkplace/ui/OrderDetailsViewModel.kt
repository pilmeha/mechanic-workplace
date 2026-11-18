package com.example.mechanicworkplace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.repository.OrdersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class OrderDetailsUiState(
    val card: OrderCardDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val fromCache: Boolean = false,
    val showDefectsDialog: Boolean = false,
    val defectQuery: String = "",
    val defectItems: List<DefectCatalogItemDto> = emptyList(),
    val isDefectsLoading: Boolean = false,
    val defectError: String? = null,
    val selectedDefect: DefectCatalogItemDto? = null,
    val isPosting: Boolean = false,
    val isAddingDefect: Boolean = false,
    val actionError: String? = null,
    val needsConfirmation: Boolean = false,
    val postQueued: Boolean = false,
    val refreshVersion: Int = 0,
    val queuedMessage: String? = null,
    val postVersion: Int = 0,
    val defectVersion: Int = 0
)

class OrderDetailsViewModel(
    private val repository: OrdersRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrderDetailsUiState())
    val state: StateFlow<OrderDetailsUiState> = _state.asStateFlow()

    private var loadToken = 0
    private var defectToken = 0
    private var defectSearchJob: Job? = null

    fun load(orderId: String, force: Boolean = false) {
        val token = ++loadToken
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.getOrder(orderId, forceRefresh = force)
                if (token != loadToken) return@launch
                _state.update {
                    it.copy(
                        card = result.card,
                        isLoading = false,
                        error = null,
                        fromCache = result.fromCache,
                        actionError = null,
                        needsConfirmation = false,
                        postQueued = false,
                        queuedMessage = null
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (token != loadToken) return@launch
                _state.update {
                    it.copy(isLoading = false, error = t.toUserMessage())
                }
            }
        }
    }

    fun refresh(orderId: String) {
        _state.update { it.copy(refreshVersion = it.refreshVersion + 1) }
        load(orderId, force = true)
    }

    fun showDefects() {
        _state.update { it.copy(showDefectsDialog = true, defectQuery = "") }
        loadDefects("")
    }

    fun hideDefects() {
        _state.update { it.copy(showDefectsDialog = false) }
    }

    fun updateDefectSearch(query: String) {
        if (_state.value.defectQuery == query) return
        _state.update { it.copy(defectQuery = query) }
        defectSearchJob?.cancel()
        defectSearchJob = viewModelScope.launch {
            delay(300)
            loadDefects(query)
        }
    }

    fun retryDefects() {
        loadDefects(_state.value.defectQuery)
    }

    fun selectDefect(defect: DefectCatalogItemDto) {
        _state.update {
            it.copy(
                selectedDefect = defect,
                showDefectsDialog = false,
                actionError = null
            )
        }
    }

    fun clearSelectedDefect() {
        _state.update { it.copy(selectedDefect = null, actionError = null) }
    }

    fun clearActionError() {
        _state.update { it.copy(actionError = null) }
    }

    /** POST /orders/{id}/post; repository re-reads the card on unknown outcomes. */
    fun postOrder(orderId: String) {
        if (_state.value.isPosting) return
        _state.update { it.copy(isPosting = true, actionError = null) }
        viewModelScope.launch {
            try {
                // TODO(stage 6): enqueue POST_ORDER instead of direct send.
                val card = repository.postOrder(orderId)
                _state.update {
                    it.copy(
                        card = card,
                        isPosting = false,
                        actionError = null,
                        postQueued = true,
                        queuedMessage = "Проведение поставлено в очередь",
                        postVersion = it.postVersion + 1
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isPosting = false, actionError = t.toUserMessage())
                }
            }
        }
    }

    /** POST /orders/{id}/defects after the catalog selection. */
    fun addDefect(orderId: String, reasonId: String, content: String?, normHour: BigDecimal?) {
        if (_state.value.isAddingDefect) return
        if (reasonId.isBlank()) {
            _state.update { it.copy(actionError = "Не выбрана причина обращения") }
            return
        }
        _state.update { it.copy(isAddingDefect = true, actionError = null, needsConfirmation = false) }
        viewModelScope.launch {
            try {
                // TODO(stage 6): enqueue ADD_DEFECT instead of direct send.
                val result = repository.addDefect(
                    orderId,
                    AddDefectRequest(
                        reasonId = reasonId,
                        content = content?.trim()?.takeIf { it.isNotEmpty() },
                        normHour = normHour
                    )
                )
                _state.update {
                    it.copy(
                        card = result.card ?: it.card,
                        isAddingDefect = false,
                        needsConfirmation = result.needsConfirmation,
                        selectedDefect = if (result.needsConfirmation) it.selectedDefect else null,
                        defectVersion = if (result.needsConfirmation) it.defectVersion else it.defectVersion + 1,
                        queuedMessage = if (result.needsConfirmation) {
                            it.queuedMessage
                        } else {
                            "Неисправность поставлена в очередь"
                        },
                        actionError = if (result.needsConfirmation) {
                            "Не удалось подтвердить добавление. Обновите карточку и проверьте вручную."
                        } else {
                            null
                        }
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isAddingDefect = false, actionError = t.toUserMessage())
                }
            }
        }
    }

    private fun loadDefects(query: String) {
        val token = ++defectToken
        _state.update { it.copy(isDefectsLoading = true, defectError = null) }
        viewModelScope.launch {
            try {
                val items = repository.getDefects(query)
                if (token != defectToken) return@launch
                _state.update {
                    it.copy(defectItems = items, isDefectsLoading = false, defectError = null)
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (token != defectToken) return@launch
                _state.update {
                    it.copy(isDefectsLoading = false, defectError = t.toUserMessage())
                }
            }
        }
    }
}

class OrderDetailsViewModelFactory(
    private val repository: OrdersRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        OrderDetailsViewModel(repository) as T
}
