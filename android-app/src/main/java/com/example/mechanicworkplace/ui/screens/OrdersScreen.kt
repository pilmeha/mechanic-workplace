package com.example.mechanicworkplace.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mechanicworkplace.AppContainer
import com.example.mechanicworkplace.data.auth.QueueGateState
import com.example.mechanicworkplace.data.local.StatusCount
import com.example.mechanicworkplace.data.remote.onecmobile.OrderListItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderStateFilter
import com.example.mechanicworkplace.ui.LogoutGuard
import com.example.mechanicworkplace.ui.OrdersUiState
import com.example.mechanicworkplace.ui.SyncBannerState
import com.example.mechanicworkplace.ui.syncBannerState
import com.example.mechanicworkplace.ui.OrdersViewModel
import com.example.mechanicworkplace.ui.OrdersViewModelFactory
import com.example.mechanicworkplace.ui.displayAmount
import com.example.mechanicworkplace.ui.displayDate
import com.example.mechanicworkplace.ui.displayStateName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    container: AppContainer,
    onOpen: (String) -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: OrdersViewModel = viewModel(
        factory = OrdersViewModelFactory(container.ordersRepository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val counts by container.syncQueueRepository.counts
        .collectAsStateWithLifecycle(initialValue = emptyList<StatusCount>())
    val activeCount by container.syncQueueRepository.activeCount
        .collectAsStateWithLifecycle(initialValue = 0)
    val gateState by container.syncQueueGate.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ-наряды") },
                actions = {
                    IconButton(onClick = {
                        viewModel.refresh()
                        scope.launch { container.syncScheduler.enqueue() }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = {
                        if (LogoutGuard.requiresConfirmation(activeCount)) confirmLogout = true else onLogout()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Выйти"
                        )
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
            if (state.fromCache) {
                OfflineBanner()
            }
            QueueBanner(
                counts = counts,
                gateState = gateState,
                onReconcile = {
                    scope.launch {
                        container.syncQueueRepository.reconcile()
                        viewModel.refresh()
                    }
                }
            )

            FilterRow(
                selected = state.filter,
                onSelect = viewModel::selectFilter
            )

            SearchField(
                value = state.search,
                onValueChange = viewModel::updateSearch,
                onSearch = viewModel::submitSearch
            )

            if (state.error != null && state.items.isNotEmpty()) {
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading && state.items.isEmpty() -> LoadingState()
                    state.error != null && state.items.isEmpty() -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::retry
                    )
                    state.items.isEmpty() -> EmptyState()
                    else -> OrderList(
                        state = state,
                        onOpen = onOpen,
                        onLoadMore = viewModel::loadMore
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Есть несинхронизированные операции") },
            text = {
                Text("$activeCount операций не синхронизированы. При выходе они будут потеряны.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Выйти и очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun QueueBanner(
    counts: List<StatusCount>,
    gateState: QueueGateState,
    onReconcile: () -> Unit
) {
    val banner = syncBannerState(counts, gateState)
    if (!banner.visible) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (banner.needsAuth) {
                Text("Требуется повторный вход", style = MaterialTheme.typography.bodySmall)
            }
            if (banner.pending > 0) {
                Text("В очереди: ${banner.pending}", style = MaterialTheme.typography.bodySmall)
            }
            if (banner.needsReconciliation > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Требуют подтверждения: ${banner.needsReconciliation}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onReconcile) { Text("Обновить и подтвердить") }
                }
            }
            if (banner.failed > 0) {
                Text(
                    "Ошибки синхронизации: ${banner.failed}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: OrderStateFilter,
    onSelect: (OrderStateFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrderStateFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label()) }
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Поиск: номер или VIN") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun OrderList(
    state: OrdersUiState,
    onOpen: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.items, key = { it.id }) { order ->
            OrderCard(
                order = order,
                stateName = order.displayStateName(state.stateNames),
                onClick = { onOpen(order.id) }
            )
        }
        if (state.hasMore || state.isLoadingMore) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = onLoadMore) { Text("Загрузить ещё") }
                    }
                }
            }
        }
        item {
            Text(
                "Всего: ${state.total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderListItemDto,
    stateName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("№ ${order.number}", fontWeight = FontWeight.Bold)
                if (order.posted) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "Проведён",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            Text(stateName, color = MaterialTheme.colorScheme.primary)
            Text(order.client?.name ?: "Клиент не указан")
            Text(
                order.vehicle?.let { "${it.model} · ${it.vin}" } ?: "Автомобиль не указан",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Планово: ${order.plannedDate.displayDate().ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Сумма работ: ${order.sumWorks.displayAmount()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Нет сети: показаны данные из кэша. Нажмите «Обновить».",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Заказ-нарядов не найдено",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun OrderStateFilter.label(): String = when (this) {
    OrderStateFilter.ALL -> "Все"
    OrderStateFilter.CURRENT -> "Активные"
    OrderStateFilter.UPCOMING -> "Предстоящие"
    OrderStateFilter.CLOSED -> "Закрытые"
}
