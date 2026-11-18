package com.example.mechanicworkplace.data.remote.onecmobile

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    @Test
    fun `redacts basic header`() {
        val sanitized = LogSanitizer.redact("Authorization: Basic dXNlcjpwYXNz")
        assertFalse(sanitized.contains("dXNlcjpwYXNz"))
        assertTrue(sanitized.contains("<redacted>"))
    }

    @Test
    fun `redacts password and contentBase64 fields`() {
        val sanitized = LogSanitizer.redact(
            """{"password":"top-secret","contentBase64":"/9j/4AAQSkZJRg=="}"""
        )
        assertFalse(sanitized.contains("top-secret"))
        assertFalse(sanitized.contains("/9j/4AAQSkZJRg=="))
    }

    @Test
    fun `keeps harmless text untouched`() {
        val text = "POST /orders -> 200"
        assertTrue(LogSanitizer.redact(text) == text)
    }
}
