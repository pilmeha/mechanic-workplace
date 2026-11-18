package com.example.mechanicworkplace.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.credentialsDataStore by preferencesDataStore(name = "mechanic_credentials")

/**
 * Persists the 1C login/password only as AES-GCM ciphertext (see [KeystoreCipher]).
 *
 * The plaintext pair is loaded lazily once into [BasicAuthProvider] by
 * [AuthSessionManager]; it is never written to Room or to logs.
 */
class CredentialsStore(
    private val context: Context,
    private val cipher: KeystoreCipher,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val encryptedCredentialsKey = stringPreferencesKey("credentials_cipher_v1")

    suspend fun save(credentials: Credentials) {
        val encrypted = cipher.encrypt(json.encodeToString(Credentials.serializer(), credentials))
        context.credentialsDataStore.edit { preferences ->
            preferences[encryptedCredentialsKey] = encrypted
        }
    }

    suspend fun load(): Credentials? {
        val encrypted = context.credentialsDataStore.data.first()[encryptedCredentialsKey] ?: return null
        return try {
            json.decodeFromString(Credentials.serializer(), cipher.decrypt(encrypted))
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            // Key was invalidated or payload is corrupted: drop it and force a new login.
            clear()
            null
        }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { preferences ->
            preferences.remove(encryptedCredentialsKey)
        }
    }

    suspend fun hasCredentials(): Boolean = load() != null
}
