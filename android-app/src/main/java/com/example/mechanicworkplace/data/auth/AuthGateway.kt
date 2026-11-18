package com.example.mechanicworkplace.data.auth

import com.example.mechanicworkplace.data.remote.onecmobile.UserDto

/** Testable seam for login/logout/session restore used by the UI layer. */
interface AuthGateway {
    suspend fun login(login: String, password: String): UserDto
    suspend fun logout()
    suspend fun restoreSession(): Boolean
}
