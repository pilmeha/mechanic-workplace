package com.example.mechanicworkplace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mechanicworkplace.data.auth.AuthState
import com.example.mechanicworkplace.ui.LoginViewModel
import com.example.mechanicworkplace.ui.LoginViewModelFactory
import com.example.mechanicworkplace.ui.screens.LoginScreen
import com.example.mechanicworkplace.ui.screens.OrderDetailsScreen
import com.example.mechanicworkplace.ui.screens.OrdersScreen
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    val authState by container.authSessionManager.state.collectAsStateWithLifecycle()
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(container.authRepository)
    )
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (container.authSessionManager.state.value is AuthState.Unknown) {
            container.authRepository.restoreSession()
        }
    }

    LaunchedEffect(authState) {
        if (authState !is AuthState.Authenticated) {
            selectedOrderId = null
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (val state = authState) {
                is AuthState.Unknown -> LoadingScreen()

                is AuthState.Authenticated -> {
                    val orderId = selectedOrderId
                    if (orderId == null) {
                        OrdersScreen(
                            container = container,
                            onOpen = { selectedOrderId = it },
                            onLogout = {
                                scope.launch {
                                    container.authRepository.logout()
                                    loginViewModel.clearError()
                                }
                            }
                        )
                    } else {
                        OrderDetailsScreen(
                            container = container,
                            orderId = orderId,
                            onBack = { selectedOrderId = null }
                        )
                    }
                }

                is AuthState.LoggedOut, is AuthState.NeedsAuth -> LoginScreen(
                    state = loginState,
                    needsAuth = state is AuthState.NeedsAuth,
                    onLogin = loginViewModel::login
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
