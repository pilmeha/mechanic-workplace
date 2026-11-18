package com.example.mechanicworkplace.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.syncGateDataStore by preferencesDataStore(name = "sync_queue_gate")

/**
 * Durable gate for the offline queue.
 *
 * When any request gets 401 the queue is paused with [QueuePauseReason.NEEDS_AUTH] so the
 * worker cannot loop over operations that will keep failing. Phase 6 wires this gate into
 * the new SyncQueue worker; the state is already exposed here for the UI banner.
 *
 * TODO(phase 6): worker must check this gate before sending, and a 401 must leave the
 * operation in NEEDS_AUTH instead of retrying it.
 */
sealed interface QueueGateState {
    data object Open : QueueGateState
    data class Paused(val reason: String) : QueueGateState
}

object QueuePauseReason {
    const val NEEDS_AUTH = "NEEDS_AUTH"
}

class SyncQueueGate(private val context: Context) {
    private val pauseReasonKey = stringPreferencesKey("pause_reason")

    private val _state = MutableStateFlow<QueueGateState>(QueueGateState.Open)
    val state: StateFlow<QueueGateState> = _state.asStateFlow()

    suspend fun restore() {
        val reason = context.syncGateDataStore.data.first()[pauseReasonKey]
        _state.value = if (reason == null) {
            QueueGateState.Open
        } else {
            QueueGateState.Paused(reason)
        }
    }

    suspend fun pauseForAuth() {
        _state.value = QueueGateState.Paused(QueuePauseReason.NEEDS_AUTH)
        context.syncGateDataStore.edit { preferences ->
            preferences[pauseReasonKey] = QueuePauseReason.NEEDS_AUTH
        }
    }

    suspend fun resume() {
        _state.value = QueueGateState.Open
        context.syncGateDataStore.edit { preferences ->
            preferences.remove(pauseReasonKey)
        }
    }
}
