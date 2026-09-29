package com.example.mechanic.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher {
    private val random = SecureRandom()
    private val iterations = 310_000
    private val keyLength = 256
    private val saltBytes = 16

    fun hash(password: String): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val salt = ByteArray(saltBytes)
        random.nextBytes(salt)
        val derived = derive(password, salt, iterations)
        return "PBKDF2-SHA256:$iterations:${encode(salt)}:${encode(derived)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split(':')
        if (parts.size != 4 || parts[0] != "PBKDF2-SHA256") return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getUrlDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getUrlDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = derive(password, salt, rounds)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, keyLength)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
