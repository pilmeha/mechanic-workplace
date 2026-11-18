package com.example.mechanicworkplace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mechanicworkplace.data.auth.AuthGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginViewModel(private val auth: AuthGateway) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(login: String, password: String) {
        if (_state.value.isLoading) return
        if (login.isBlank() || password.isBlank()) {
            _state.value = LoginUiState(error = "Введите логин и пароль")
            return
        }
        viewModelScope.launch {
            _state.value = LoginUiState(isLoading = true)
            try {
                auth.login(login, password)
                _state.value = LoginUiState()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.value = LoginUiState(error = t.toUserMessage(forLogin = true))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

class LoginViewModelFactory(private val auth: AuthGateway) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(auth) as T
}
