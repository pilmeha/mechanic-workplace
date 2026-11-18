package com.example.mechanicworkplace.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogoutGuardTest {
    @Test
    fun `logout requires confirmation only with active operations`() {
        assertFalse(LogoutGuard.requiresConfirmation(0))
        assertTrue(LogoutGuard.requiresConfirmation(1))
        assertTrue(LogoutGuard.requiresConfirmation(5))
    }
}
