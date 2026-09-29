package com.example.mechanicworkplace.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mechanicworkplace.AppContainer
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = AppContainer.get(applicationContext).repository
        return try {
            repository.syncQueueOnce()
            Result.success()
        } catch (t: Throwable) {
            if (t is IOException || t is HttpRequestTimeoutException || t.message?.contains("timeout", true) == true) {
                Result.retry()
            } else {
                Result.retry()
            }
        }
    }
}
