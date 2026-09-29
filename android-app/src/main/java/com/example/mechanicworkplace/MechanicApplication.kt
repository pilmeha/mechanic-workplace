package com.example.mechanicworkplace

import android.app.Application
import androidx.work.WorkManager

class MechanicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.get(this)
        // WorkManager uses its normal default initialization.
        WorkManager.getInstance(this)
    }
}
