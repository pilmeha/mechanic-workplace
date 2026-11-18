package com.example.mechanicworkplace

import android.app.Application
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MechanicApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.get(this)
        WorkManager.getInstance(this)
        // Recover stale SENDING rows at app start; they must be reconciled manually.
        appScope.launch {
            runCatching {
                container.syncQueueRepository.resetStaleSending()
                container.syncScheduler.enqueue()
            }
        }
    }
}
