package com.example.mechanicworkplace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mechanicworkplace.repository.MechanicRepository
import com.example.mechanicworkplace.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: MechanicRepository) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun checkSession() {
        viewModelScope.launch {
            _state.value = if (repository.isLoggedIn()) AuthState.LoggedIn else AuthState.Idle
        }
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                val result = repository.login(login, password)
                _state.value = AuthState.Success(result.user)
            } catch (t: Throwable) {
                _state.value = AuthState.Error(t.message ?: "Ошибка авторизации")
            }
        }
    }

    fun reset() {
        _state.value = AuthState.Idle
    }
}

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data object LoggedIn : AuthState
    data class Success(val user: UserDto) : AuthState
    data class Error(val message: String) : AuthState
}

class HomeViewModel(private val repository: MechanicRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()
    val pendingCount = repository.pendingCount

    fun load() {
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                _state.value = HomeState.Success(repository.loadHome())
            } catch (t: Throwable) {
                _state.value = HomeState.Error(t.message ?: "Не удалось загрузить заказ-наряды")
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            runCatching { repository.syncQueueOnce() }
            load()
        }
    }
}

sealed interface HomeState {
    data object Loading : HomeState
    data class Success(val data: WorkOrderListDto) : HomeState
    data class Error(val message: String) : HomeState
}

class WorkOrderViewModel(private val repository: MechanicRepository) : ViewModel() {
    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()
    private var currentId: String? = null
    val error = MutableStateFlow<String?>(null)

    fun load(id: String) {
        if (currentId == id && _state.value is DetailsState.Success) return
        currentId = id
        viewModelScope.launch {
            _state.value = DetailsState.Loading
            try {
                _state.value = DetailsState.Success(repository.loadDetails(id))
            } catch (t: Throwable) {
                _state.value = DetailsState.Error(t.message ?: "Не удалось открыть заказ-наряд")
            }
        }
    }

    fun start(operationId: String) = mutate {
        repository.startOperation(currentId!!, operationId)
    }

    fun complete(operationId: String, description: String?) = mutate {
        repository.completeOperation(currentId!!, operationId, description)
    }

    fun issue(operationId: String?, description: String, additional: Boolean) = mutate {
        repository.createIssue(currentId!!, operationId, description, additional)
    }

    fun addGallery(uris: List<android.net.Uri>) = mutate {
        repository.addGalleryFiles(currentId!!, selectedOperationIdForPhoto, uris)
    }

    fun addCamera(file: java.io.File) = mutate {
        repository.addCameraFile(currentId!!, selectedOperationIdForPhoto, file)
    }

    private var selectedOperationIdForPhoto: String? = null
    fun setPhotoOperation(operationId: String?) {
        selectedOperationIdForPhoto = operationId
    }

    fun pendingPhotos(workOrderId: String) = repository.observePendingPhotos(workOrderId)
    fun pendingForOrder(workOrderId: String) = repository.observeWorkOrderPending(workOrderId)

    fun reload() {
        currentId?.let { id ->
            viewModelScope.launch {
                try { _state.value = DetailsState.Success(repository.loadDetails(id)) }
                catch (t: Throwable) { error.value = t.message ?: "Ошибка" }
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                reload()
            } catch (t: Throwable) {
                error.value = t.message ?: "Операция не выполнена"
            }
        }
    }
}

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Success(val data: WorkOrderDetailsDto) : DetailsState
    data class Error(val message: String) : DetailsState
}

class AppViewModelFactory(
    private val repository: MechanicRepository,
    private val type: Type
) : ViewModelProvider.Factory {
    enum class Type { AUTH, HOME, DETAILS }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (type) {
        Type.AUTH -> AuthViewModel(repository)
        Type.HOME -> HomeViewModel(repository)
        Type.DETAILS -> WorkOrderViewModel(repository)
    } as T
}
