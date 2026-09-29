package com.example.mechanicworkplace.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class SessionStore(private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("token")
        val userJson = stringPreferencesKey("user_json")
    }

    suspend fun save(token: String, userJson: String) {
        context.sessionDataStore.edit {
            it[Keys.token] = token
            it[Keys.userJson] = userJson
        }
    }

    suspend fun token(): String? = context.sessionDataStore.data
        .map { it[Keys.token] }
        .first()

    suspend fun userJson(): String? = context.sessionDataStore.data
        .map { it[Keys.userJson] }
        .first()

    suspend fun isLoggedIn(): Boolean = token() != null

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
