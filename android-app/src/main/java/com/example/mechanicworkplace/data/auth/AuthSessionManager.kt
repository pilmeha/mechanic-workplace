package com.example.mechanicworkplace.data.auth

import com.example.mechanicworkplace.data.remote.onecmobile.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the in-memory credentials, their encrypted persistence, and the auth/queue state.
 *
 * 401 policy: clear the in-memory pair, delete the ciphertext, pause the sync queue with
 * NEEDS_AUTH, and publish [AuthState.NeedsAuth] so the UI can navigate to login.
 */
class AuthSessionManager(
    private val authProvider: BasicAuthProvider,
    private val credentialsStore: CredentialsStore,
    private val queueGate: SyncQueueGate
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun restoreSession(): Boolean {
        runCatching { queueGate.restore() }
        val credentials = runCatching { credentialsStore.load() }.getOrNull()
        if (credentials == null) {
            authProvider.clear()
            _state.value = AuthState.LoggedOut
            return false
        }
        authProvider.set(credentials)
        _state.value = AuthState.Authenticated(null)
        return true
    }

    suspend fun onLogin(credentials: Credentials, user: UserDto) {
        authProvider.set(credentials)
        credentialsStore.save(credentials)
        runCatching { queueGate.resume() }
        _state.value = AuthState.Authenticated(user)
    }

    suspend fun onUnauthorized() {
        authProvider.clear()
        runCatching { credentialsStore.clear() }
        runCatching { queueGate.pauseForAuth() }
        _state.value = AuthState.NeedsAuth
    }

    suspend fun logout() {
        authProvider.clear()
        runCatching { credentialsStore.clear() }
        runCatching { queueGate.resume() }
        _state.value = AuthState.LoggedOut
    }

    fun isAuthenticated(): Boolean = authProvider.isAuthenticated()
}
