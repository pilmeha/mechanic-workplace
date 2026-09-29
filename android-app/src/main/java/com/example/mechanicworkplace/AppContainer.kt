package com.example.mechanicworkplace

import android.content.Context
import androidx.room.Room
import com.example.mechanicworkplace.data.local.AppDatabase
import com.example.mechanicworkplace.data.remote.ApiClient
import com.example.mechanicworkplace.data.session.SessionStore
import com.example.mechanicworkplace.repository.MechanicRepository
import com.example.mechanicworkplace.sync.SyncScheduler

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "mechanic-workplace.db"
    ).build()
    val sessionStore = SessionStore(appContext)
    val apiClient = ApiClient(sessionStore)
    val syncScheduler = SyncScheduler(appContext)
    val repository = MechanicRepository(
        context = appContext,
        db = database,
        api = apiClient,
        session = sessionStore,
        scheduler = syncScheduler
    )

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
