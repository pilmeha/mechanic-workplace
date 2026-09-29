package com.example.mechanicworkplace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mechanicworkplace.data.local.SyncQueueEntity
import com.example.mechanicworkplace.model.*
import com.example.mechanicworkplace.ui.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MechanicApp() }
    }
}

@Composable
fun MechanicApp() {
    val context = LocalContext.current
    val container = remember { AppContainer.get(context) }
    val authVm: AuthViewModel = viewModel(factory = AppViewModelFactory(container.repository, AppViewModelFactory.Type.AUTH))
    val authState by authVm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { authVm.checkSession() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (authState) {
                AuthState.Idle, AuthState.Loading, is AuthState.Error -> LoginScreen(
                    state = authState,
                    onLogin = authVm::login
                )
                AuthState.LoggedIn, is AuthState.Success -> MainShell(
                    repositoryContainer = container,
                    onLogout = {
                        scope.launch {
                            container.repository.logout()
                            authVm.reset()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AuthState, onLogin: (String, String) -> Unit) {
    var login by rememberSaveable { mutableStateOf("mechanic01") }
    var password by rememberSaveable { mutableStateOf("demo12345") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Рабочее место автомеханика", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Этап 3–4: Android + Offline Sync", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(login, { login = it }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onLogin(login, password) },
            enabled = state !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state is AuthState.Loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Войти")
        }

        if (state is AuthState.Error) {
            Spacer(Modifier.height(12.dp))
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Text("Demo: mechanic01 / demo12345", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MainShell(
    repositoryContainer: AppContainer,
    onLogout: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var selectedOrder by rememberSaveable { mutableStateOf<String?>(null) }

    if (screen == "home") {
        HomeScreen(
            repositoryContainer,
            onOpen = { selectedOrder = it; screen = "details" },
            onLogout = onLogout
        )
    } else {
        WorkOrderScreen(
            repositoryContainer,
            workOrderId = selectedOrder!!,
            onBack = { screen = "home" }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    container: AppContainer,
    onOpen: (String) -> Unit,
    onLogout: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = AppViewModelFactory(container.repository, AppViewModelFactory.Type.HOME))
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle(initialValue = 0)

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заказ-наряды") },
                actions = {
                    if (pending > 0) {
                        Icon(Icons.Default.CloudOff, contentDescription = "В очереди: $pending")
                        Text("$pending", modifier = Modifier.padding(end = 8.dp))
                    }
                    IconButton(onClick = vm::sync) { Icon(Icons.Default.Refresh, null) }
                    TextButton(onClick = onLogout) { Text("Выйти") }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            HomeState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is HomeState.Error -> ErrorState(s.message, Modifier.padding(padding), vm::load)
            is HomeState.Success -> {
                val all = buildList {
                    s.data.current?.let { add(it) }
                    addAll(s.data.upcoming)
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    s.data.current?.let { current ->
                        item { SectionTitle("Текущий заказ-наряд") }
                        item {
                            WorkOrderCard(current, highlighted = true, onClick = { onOpen(current.id) })
                        }
                    }
                    item { SectionTitle("Ближайшие автомобили") }
                    items(s.data.upcoming.take(3), key = { it.id }) {
                        WorkOrderCard(it, highlighted = false, onClick = { onOpen(it.id) })
                    }
                    if (all.isEmpty()) item { Text("Назначенных заказ-нарядов нет") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun WorkOrderCard(item: WorkOrderSummaryDto, highlighted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("№ ${item.number}", fontWeight = FontWeight.Bold)
            Text("${item.vehicle.brand} ${item.vehicle.model}")
            Text("VIN: ${item.vehicle.vin}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(formatDate(item.scheduledAt))
            Text(statusText(item.status), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkOrderScreen(container: AppContainer, workOrderId: String, onBack: () -> Unit) {
    val vm: WorkOrderViewModel = viewModel(factory = AppViewModelFactory(container.repository, AppViewModelFactory.Type.DETAILS))
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingForOrder(workOrderId).collectAsStateWithLifecycle(initialValue = 0)
    val pendingPhotos by vm.pendingPhotos(workOrderId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showIssue by remember { mutableStateOf(false) }
    var completeOperation by remember { mutableStateOf<OperationDto?>(null) }
    val context = LocalContext.current

    LaunchedEffect(workOrderId) { vm.load(workOrderId) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addGallery(uris)
    }

    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        if (success && file != null) vm.addCamera(file)
        else file?.delete()
        cameraFile = null
    }

    fun captureCamera() {
        val file = File.createTempFile("mechanic-camera-", ".jpg", context.cacheDir)
        cameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карточка заказ-наряда") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { if (pending > 0) Text("Очередь: $pending", modifier = Modifier.padding(end = 12.dp)) }
            )
        }
    ) { padding ->
        when (val s = state) {
            DetailsState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is DetailsState.Error -> ErrorState(s.message, Modifier.padding(padding), vm::reload)
            is DetailsState.Success -> {
                DetailContent(
                    detail = s.data,
                    pendingPhotos = pendingPhotos,
                    onStart = vm::start,
                    onComplete = { completeOperation = it },
                    onIssue = { showIssue = true },
                    onGallery = { operationId -> vm.setPhotoOperation(operationId); galleryLauncher.launch("image/*") },
                    onCamera = { operationId -> vm.setPhotoOperation(operationId); captureCamera() }
                )
            }
        }
    }

    if (completeOperation != null) {
        CompleteDialog(
            operation = completeOperation!!,
            onDismiss = { completeOperation = null },
            onConfirm = { description ->
                vm.complete(completeOperation!!.id, description)
                completeOperation = null
            }
        )
    }

    if (showIssue) {
        IssueDialog(
            onDismiss = { showIssue = false },
            onConfirm = { description, additional ->
                vm.issue(null, description, additional)
                showIssue = false
            }
        )
    }
}

@Composable
private fun DetailContent(
    detail: WorkOrderDetailsDto,
    pendingPhotos: List<SyncQueueEntity>,
    onStart: (String) -> Unit,
    onComplete: (OperationDto) -> Unit,
    onIssue: () -> Unit,
    onGallery: (String?) -> Unit,
    onCamera: (String?) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("№ ${detail.number}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(statusText(detail.status), color = MaterialTheme.colorScheme.primary)
        }
        item {
            InfoCard("Автомобиль") {
                Text("${detail.vehicle.brand} ${detail.vehicle.model} (${detail.vehicle.year ?: "—"})")
                Text("VIN: ${detail.vehicle.vin}")
                Text("Пробег: ${detail.vehicle.mileage} км")
            }
        }
        item {
            InfoCard("Клиент") {
                Text(detail.customerName)
                detail.complaint?.let { Text("Обращение: $it") }
            }
        }
        item { Text("Назначенные работы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(detail.operations, key = { it.id }) { op ->
            OperationCard(op, onStart, onComplete)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onIssue) { Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(6.dp)); Text("Неисправность") }
                OutlinedButton(onClick = { onGallery(null) }) { Text("+ Фото") }
                OutlinedButton(onClick = { onCamera(null) }) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(4.dp)); Text("Камера") }
            }
        }
        item {
            Text("Фотографии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (detail.attachments.isEmpty() && pendingPhotos.isEmpty()) {
                Text("Фотографий пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.attachments.forEach { attachment ->
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                attachment.originalName,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    pendingPhotos.forEach { q ->
                        AsyncImage(
                            model = File(q.localFilePath!!),
                            contentDescription = "Ожидает синхронизации: ${q.fileName}",
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Text("Синхронизация: ${pendingPhotos.size} фото ожидают отправки", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (detail.issues.isNotEmpty()) {
            item {
                Text("Обнаруженные неисправности", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.issues.forEach { issue ->
                        AssistChip(onClick = {}, label = { Text(issue.description) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationCard(op: OperationDto, onStart: (String) -> Unit, onComplete: (OperationDto) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(op.name, fontWeight = FontWeight.SemiBold)
            Text(statusText(op.status), color = MaterialTheme.colorScheme.primary)
            op.description?.let { Text(it) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (op.status) {
                    "ASSIGNED" -> Button(onClick = { onStart(op.id) }) { Text("Начать работу") }
                    "IN_PROGRESS" -> Button(onClick = { onComplete(op) }) { Text("Завершить") }
                    "COMPLETED" -> Text("Работа завершена", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CompleteDialog(operation: OperationDto, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var description by rememberSaveable { mutableStateOf(operation.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Завершить: ${operation.name}") },
        text = { OutlinedTextField(description, { description = it }, label = { Text("Описание выполненной работы") }, minLines = 4) },
        confirmButton = { Button(onClick = { onConfirm(description) }) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun IssueDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var description by rememberSaveable { mutableStateOf("") }
    var additional by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обнаруженная неисправность") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Описание") }, minLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(additional, { additional = it })
                    Text("Требуется дополнительная работа")
                }
            }
        },
        confirmButton = { Button(enabled = description.isNotBlank(), onClick = { onConfirm(description, additional) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ErrorState(message: String, modifier: Modifier, retry: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = retry) { Text("Повторить") }
    }
}

private fun statusText(status: String) = when (status) {
    "ASSIGNED" -> "Назначено"
    "IN_PROGRESS" -> "В работе"
    "COMPLETED" -> "Выполнено"
    else -> status
}

private fun formatDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}.getOrElse { value }
