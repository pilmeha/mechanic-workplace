package com.example.mechanicworkplace.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mechanicworkplace.AppContainer
import kotlinx.coroutines.CancellationException

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext)
        return try {
            when (val result = container.syncQueueProcessor.run()) {
                is QueueRunResult.Future -> {
                    val delay = (result.nextAt - System.currentTimeMillis()).coerceAtLeast(0)
                    container.syncScheduler.enqueue(delay)
                    Result.success()
                }
                QueueRunResult.Idle, QueueRunResult.Paused -> {
                    if (WorkerPolicy.shouldRetry(container.syncQueueProcessor.hasEligibleNow())) {
                        Result.retry()
                    } else {
                        Result.success()
                    }
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
            if (WorkerPolicy.shouldRetry(container.syncQueueProcessor.hasEligibleNow())) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}
