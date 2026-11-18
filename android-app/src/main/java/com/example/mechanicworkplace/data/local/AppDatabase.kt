package com.example.mechanicworkplace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedOrderEntity::class,
        CachedOrderDetailsEntity::class,
        CachedPhotoEntity::class,
        CachedStateEntity::class,
        CachedDefectEntity::class,
        SyncOperationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderCacheDao(): OrderCacheDao
    abstract fun photoCacheDao(): PhotoCacheDao
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mechanic-workplace.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
