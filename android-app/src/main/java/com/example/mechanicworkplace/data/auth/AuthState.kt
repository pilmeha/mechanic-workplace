package com.example.mechanicworkplace.data.auth

import com.example.mechanicworkplace.data.remote.onecmobile.UserDto

/**
 * Authentication state exposed to the UI layer.
 *
 * The new contract has no token: [Authenticated] means a Basic credential pair is available
 * in memory. [NeedsAuth] is set after any 401 and must send the user back to the login screen.
 */
sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class Authenticated(val user: UserDto?) : AuthState
    data object NeedsAuth : AuthState
}
