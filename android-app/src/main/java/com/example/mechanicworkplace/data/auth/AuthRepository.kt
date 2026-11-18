package com.example.mechanicworkplace.data.auth

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileApi
import com.example.mechanicworkplace.data.local.CacheCleaner
import com.example.mechanicworkplace.data.remote.onecmobile.UserDto
import com.example.mechanicworkplace.sync.SyncQueueRepository

/**
 * Login/logout orchestration for the Basic-auth 1C API.
 *
 * The provider is populated before `/auth/login` because the endpoint validates the same
 * Basic header as every other call; it has no request body.
 */
class AuthRepository(
    private val api: OneCMobileApi,
    private val authProvider: BasicAuthProvider,
    private val authSession: AuthSessionManager,
    private val syncQueue: SyncQueueRepository? = null,
    private val cacheCleaner: CacheCleaner? = null
) : AuthGateway {
    override suspend fun login(login: String, password: String): UserDto {
        val credentials = Credentials(login = login.trim(), password = password)
        authProvider.set(credentials)
        return try {
            val user = api.login()
            authSession.onLogin(credentials, user)
            runCatching { syncQueue?.resumeAfterLogin() }
            user
        } catch (t: Throwable) {
            authProvider.clear()
            throw t
        }
    }

    /**
     * `/auth/logout` is a no-op on the server, so local cleanup must happen even if it fails.
     */
    override suspend fun logout() {
        if (authProvider.isAuthenticated()) {
            runCatching { api.logout() }
        }
        authSession.logout()
        runCatching { cacheCleaner?.clearAll() }
    }

    override suspend fun restoreSession(): Boolean = authSession.restoreSession()
}
