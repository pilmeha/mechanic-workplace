package com.example.mechanicworkplace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mechanicworkplace.data.remote.onecmobile.OrderListItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderStateFilter
import com.example.mechanicworkplace.data.remote.onecmobile.PageParams
import com.example.mechanicworkplace.repository.OrdersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val items: List<OrderListItemDto> = emptyList(),
    val total: Int = 0,
    val filter: OrderStateFilter = OrderStateFilter.ALL,
    val search: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val fromCache: Boolean = false,
    val hasMore: Boolean = false,
    val stateNames: Map<String, String> = emptyMap()
)

// TODO(contract-clarification): the old product asked for "current order + 3 nearest
// vehicles". In the 1C model this can only be derived from orders: current = first item of
// `state=current`; "nearest vehicles" are order.vehicle values, not a separate car list.
// Confirm the intended definition before changing the UI.
//
// TODO(contract-clarification): GET /orders accepts only current/upcoming/closed; the
// /states catalog cannot be used as an arbitrary state filter. The chips follow the server
// contract, while /states is used only as a display-name fallback.
class OrdersViewModel(
    private val repository: OrdersRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    private var loadToken = 0
    private var searchJob: Job? = null

    init {
        loadStates()
        load(reset = true)
    }

    fun refresh() {
        load(reset = true, force = true)
    }

    fun retry() {
        load(reset = true)
    }

    fun selectFilter(filter: OrderStateFilter) {
        if (_state.value.filter == filter) return
        searchJob?.cancel()
        _state.update {
            it.copy(
                filter = filter,
                items = emptyList(),
                total = 0,
                hasMore = false,
                error = null
            )
        }
        load(reset = true)
    }

    /**
     * Debounced search. The field updates immediately; the request fires after 350 ms.
     * The user can also submit explicitly (keyboard action).
     */
    fun updateSearch(query: String) {
        if (_state.value.search == query) return
        _state.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(reset = true)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean, force: Boolean = false) {
        val token = ++loadToken
        val current = _state.value
        val offset = if (reset) 0 else current.items.size
        _state.update {
            if (reset) {
                it.copy(isLoading = true, isLoadingMore = false, error = null)
            } else {
                it.copy(isLoadingMore = true, error = null)
            }
        }
        viewModelScope.launch {
            try {
                val result = repository.getOrders(
                    filter = current.filter,
                    search = current.search,
                    limit = PAGE_SIZE,
                    offset = offset,
                    forceRefresh = force
                )
                if (token != loadToken) return@launch
                val page = result.page
                val merged = if (reset) page.items else current.items + page.items
                _state.update {
                    it.copy(
                        items = merged,
                        total = page.total,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        fromCache = result.fromCache,
                        hasMore = merged.size < page.total
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (token != loadToken) return@launch
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = t.toUserMessage()
                    )
                }
            }
        }
    }

    private fun loadStates() {
        viewModelScope.launch {
            try {
                val states = repository.getStates()
                _state.update { current ->
                    current.copy(stateNames = states.associate { it.id to it.name })
                }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // State names are optional: the order card already carries state.name.
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = PageParams.DEFAULT_LIMIT
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}

class OrdersViewModelFactory(
    private val repository: OrdersRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        OrdersViewModel(repository) as T
}
