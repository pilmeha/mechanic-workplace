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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mechanicworkplace.AppContainer
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.DefectDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.data.remote.onecmobile.VehicleDto
import com.example.mechanicworkplace.data.remote.onecmobile.WorkDto
import com.example.mechanicworkplace.ui.OrderDetailsUiState
import com.example.mechanicworkplace.ui.OrderDetailsViewModel
import com.example.mechanicworkplace.ui.OrderDetailsViewModelFactory
import com.example.mechanicworkplace.ui.displayAmount
import com.example.mechanicworkplace.ui.displayDate

// TODO(contract-clarification): the old UI had "start work / complete work" per operation.
// The 1C contract replaces that with PUT /orders/{id}/works plus a separate POST /post.
// Batch 2 implements editing existing works, post, and defect creation.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailsScreen(
    container: AppContainer,
    orderId: String,
    onBack: () -> Unit
) {
    val viewModel: OrderDetailsViewModel = viewModel(
        key = "order-details-$orderId",
        factory = OrderDetailsViewModelFactory(container.ordersRepository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingWorks by rememberSaveable { mutableStateOf(false) }
    var showPostConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        viewModel.load(orderId)
    }
    LaunchedEffect(state.card?.posted) {
        if (state.card?.posted == true) editingWorks = false
    }

    val card = state.card
    if (editingWorks && card != null) {
        WorksEditorScreen(
            orderId = orderId,
            repository = container.ordersRepository,
            onSaved = {
                editingWorks = false
                viewModel.refresh(orderId)
            },
            onCancel = { editingWorks = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ-наряд") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(orderId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && state.card == null -> Loading()
                state.error != null && state.card == null -> Error(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(orderId) }
                )
                state.card != null -> DetailsContent(
                    state = state,
                    orderId = orderId,
                    container = container,
                    onOpenDefects = viewModel::showDefects,
                    onEditWorks = {
                        viewModel.clearActionError()
                        editingWorks = true
                    },
                    onPost = {
                        viewModel.clearActionError()
                        showPostConfirm = true
                    },
                    onRefresh = { viewModel.refresh(orderId) }
                )
            }
        }
    }

    if (state.showDefectsDialog) {
        DefectsCatalogDialog(
            state = state,
            onQueryChange = viewModel::updateDefectSearch,
            onRetry = viewModel::retryDefects,
            onSelect = viewModel::selectDefect,
            onDismiss = viewModel::hideDefects
        )
    }

    if (showPostConfirm) {
        AlertDialog(
            onDismissRequest = { showPostConfirm = false },
            title = { Text("Провести заказ-наряд?") },
            text = {
                Text("После проведения заказ-наряд нельзя редактировать. Продолжить?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPostConfirm = false
                        viewModel.postOrder(orderId)
                    }
                ) { Text("Провести") }
            },
            dismissButton = {
                TextButton(onClick = { showPostConfirm = false }) { Text("Отмена") }
            }
        )
    }

    state.selectedDefect?.let { defect ->
        AddDefectDialog(
            defect = defect,
            isSaving = state.isAddingDefect,
            needsConfirmation = state.needsConfirmation,
            error = state.actionError,
            onConfirm = { content, normHour ->
                viewModel.addDefect(orderId, defect.id, content, normHour)
            },
            onDismiss = viewModel::clearSelectedDefect
        )
    }
}

@Composable
private fun DetailsContent(
    state: OrderDetailsUiState,
    orderId: String,
    container: AppContainer,
    onOpenDefects: () -> Unit,
    onEditWorks: () -> Unit,
    onPost: () -> Unit,
    onRefresh: () -> Unit
) {
    val card = state.card ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.fromCache) {
            item {
                Banner("Нет сети: показаны данные из кэша.")
            }
        }
        state.error?.let { message ->
            item {
                Banner("Не удалось обновить: $message")
            }
        }
        item { Header(card) }
        if (!card.posted) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEditWorks,
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    ) { Text("Редактировать работы") }
                    Button(
                        onClick = onPost,
                        enabled = !state.isPosting && !state.postQueued,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isPosting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Провести")
                        }
                    }
                }
            }
        }
        state.actionError?.takeIf { !state.isAddingDefect && !state.isPosting }
            ?.let { message ->
                item { Banner(message) }
            }
        state.queuedMessage?.let { message ->
            item { Banner(message) }
        }
        if (state.needsConfirmation) {
            item {
                Column {
                    Banner("Добавление неисправности не подтверждено. Проверьте карточку вручную.")
                    TextButton(onClick = onRefresh) { Text("Обновить карточку") }
                }
            }
        }
        item { ClientCard(card) }
        if (card.summaryOrder != null) {
            item {
                InfoCard("Сводный заказ") {
                    Text(card.summaryOrder.number)
                }
            }
        }
        item {
            InfoCard("Автомобили") {
                if (card.vehicles.isEmpty()) {
                    Text("Автомобили не указаны")
                } else {
                    card.vehicles.forEach { vehicle -> VehicleRow(vehicle) }
                }
            }
        }
        card.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                InfoCard("Описание") { Text(description) }
            }
        }

        item { SectionTitle("Работы") }
        if (card.works.isEmpty()) {
            item { Text("Работы не указаны", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            itemsIndexed(
                items = card.works,
                key = { index, work -> work.lineId ?: "work-$index" }
            ) { _, work ->
                WorkCard(work)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Неисправности", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!card.posted) {
                    OutlinedButton(onClick = onOpenDefects) { Text("Добавить") }
                }
            }
        }
        if (card.defects.isEmpty()) {
            item { Text("Неисправности не указаны", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            itemsIndexed(
                items = card.defects,
                key = { index, defect -> "${defect.reasonId}-$index" }
            ) { _, defect ->
                DefectCard(defect)
            }
        }

        item {
            PhotoSection(
                orderId = orderId,
                repository = container.photoRepository,
                resolver = container.photoResolver,
                canUpload = !card.posted,
                refreshVersion = state.refreshVersion
            )
        }

        if (card.posted) {
            item {
                Banner("Заказ-наряд проведён. Редактирование недоступно.")
            }
        }
    }
}

@Composable
private fun Header(card: OrderCardDto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "№ ${card.number}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (card.posted) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "Проведён",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        Text(card.state.name, color = MaterialTheme.colorScheme.primary)
        card.date?.displayDate()?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ClientCard(card: OrderCardDto) {
    InfoCard("Клиент") {
        Text(card.client?.name ?: "Клиент не указан")
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleDto) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text("${vehicle.model}  ·  ${vehicle.vin}")
    }
}

@Composable
private fun WorkCard(work: WorkDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(work.work.name, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                work.quantity?.let { Text("Кол-во: ${it.toPlainString()}", style = MaterialTheme.typography.bodySmall) }
                work.price?.let { Text("Цена: ${it.displayAmount()}", style = MaterialTheme.typography.bodySmall) }
                work.sum?.let { Text("Сумма: ${it.displayAmount()}", style = MaterialTheme.typography.bodySmall) }
            }
            work.vatRate?.takeIf { it.isNotBlank() }?.let {
                Text("НДС: $it", style = MaterialTheme.typography.bodySmall)
            }
            work.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            work.executors.forEach { executor ->
                val details = buildList {
                    executor.name?.takeIf { it.isNotBlank() }?.let { add(it) }
                    executor.shopName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    executor.percent?.let { add("$it%") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        "Исполнитель: ${details.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DefectCard(defect: DefectDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(defect.reason ?: defect.reasonId, fontWeight = FontWeight.SemiBold)
            defect.content?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            val details = buildList {
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
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Banner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Error(message: String, onRetry: () -> Unit) {
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
