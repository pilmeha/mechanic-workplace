package com.example.mechanicworkplace.ui

object LogoutGuard {
    fun requiresConfirmation(activeSyncOperations: Int): Boolean = activeSyncOperations > 0
}
