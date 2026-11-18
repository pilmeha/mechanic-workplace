package com.example.mechanicworkplace.data.auth

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory holder for the credentials of the current 1C user.
 *
 * The new contract has no JWT/session: every request must carry
 * `Authorization: Basic base64(login:password)`. The HTTP client asks this provider
 * for the header value for each request (including binary photo downloads).
 */
class BasicAuthProvider {
    private val credentialsRef = AtomicReference<Credentials?>(null)

    fun set(credentials: Credentials) {
        credentialsRef.set(credentials)
    }

    fun clear() {
        credentialsRef.set(null)
    }

    fun current(): Credentials? = credentialsRef.get()

    fun isAuthenticated(): Boolean = credentialsRef.get() != null

    /**
     * Returns the value for the `Authorization` header or null when the user is logged out.
     * Constructed on demand; no encoded value is cached anywhere.
     */
    fun headerValue(): String? {
        val credentials = credentialsRef.get() ?: return null
        val raw = "${credentials.login}:${credentials.password}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}
