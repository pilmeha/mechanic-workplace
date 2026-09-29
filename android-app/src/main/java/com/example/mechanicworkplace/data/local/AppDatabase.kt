package com.example.mechanicworkplace.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedWorkOrderEntity::class,
        CachedWorkOrderDetailsEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workOrderDao(): WorkOrderCacheDao
    abstract fun syncQueueDao(): SyncQueueDao
}
