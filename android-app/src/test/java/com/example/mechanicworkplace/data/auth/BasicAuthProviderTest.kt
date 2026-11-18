package com.example.mechanicworkplace.data.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class BasicAuthProviderTest {

    @Test
    fun `builds Basic header from current credentials`() {
        val provider = BasicAuthProvider()
        assertNull(provider.headerValue())
        assertFalse(provider.isAuthenticated())

        provider.set(Credentials(login = "mechanic01", password = "secret"))
        val expected = "Basic " + Base64.getEncoder()
            .encodeToString("mechanic01:secret".toByteArray(Charsets.UTF_8))
        assertEquals(expected, provider.headerValue())
        assertEquals("mechanic01", provider.current()?.login)
    }

    @Test
    fun `clear removes credentials and header`() {
        val provider = BasicAuthProvider()
        provider.set(Credentials("user", "pass"))
        provider.clear()

        assertNull(provider.headerValue())
        assertNull(provider.current())
    }

    @Test
    fun `toString never exposes password`() {
        val credentials = Credentials("user", "top-secret")
        assertEquals("Credentials(login=user, password=<redacted>)", credentials.toString())
    }
}
