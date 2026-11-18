package com.example.mechanicworkplace.data.auth

import kotlinx.serialization.Serializable

/**
 * 1C infobase credentials used for HTTP Basic authentication.
 *
 * Never log this object and never persist it in plaintext; [CredentialsStore] stores only
 * an AES-GCM ciphertext produced by [KeystoreCipher].
 */
@Serializable
data class Credentials(
    val login: String,
    val password: String
) {
    override fun toString(): String = "Credentials(login=$login, password=<redacted>)"
}
