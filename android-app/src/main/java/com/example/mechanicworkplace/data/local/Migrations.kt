package com.example.mechanicworkplace.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 stored JWT-era caches and the old sync_queue. Those operations cannot be replayed
 * against the 1C API, so the migration drops them and creates the new cache/queue schema.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `sync_queue`")
        db.execSQL("DROP TABLE IF EXISTS `cached_work_orders`")
        db.execSQL("DROP TABLE IF EXISTS `cached_work_order_details`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_orders` (
                `id` TEXT NOT NULL,
                `number` TEXT NOT NULL,
                `date` TEXT,
                `stateCode` TEXT NOT NULL,
                `stateName` TEXT NOT NULL,
                `clientId` TEXT,
                `clientName` TEXT,
                `vehicleId` TEXT,
                `vehicleModel` TEXT,
                `vehicleVin` TEXT,
                `plannedDate` TEXT,
                `sumWorks` TEXT,
                `posted` INTEGER NOT NULL,
                `json` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_stateName` ON `cached_orders` (`stateName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_plannedDate` ON `cached_orders` (`plannedDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_number` ON `cached_orders` (`number`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_vehicleVin` ON `cached_orders` (`vehicleVin`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_order_details` (
                `id` TEXT NOT NULL,
                `posted` INTEGER NOT NULL,
                `json` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_photos` (
                `orderId` TEXT NOT NULL,
                `photoId` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `uploadedBy` TEXT,
                `uploadedAt` TEXT,
                `canDelete` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`orderId`, `photoId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_photos_orderId` ON `cached_photos` (`orderId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_states` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_defects` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT,
                `type` TEXT,
                `normHour` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_defects_name` ON `cached_defects` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_defects_code` ON `cached_defects` (`code`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_operations` (
                `id` TEXT NOT NULL,
                `orderId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `payloadJson` TEXT,
                `localFilePath` TEXT,
                `fileName` TEXT,
                `contentType` TEXT,
                `sizeBytes` INTEGER,
                `sha256` TEXT,
                `baselineJson` TEXT,
                `attempts` INTEGER NOT NULL,
                `lastError` TEXT,
                `nextAttemptAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_operations_orderId_type_status_createdAt` " +
                "ON `sync_operations` (`orderId`, `type`, `status`, `createdAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_operations_status_nextAttemptAt` " +
                "ON `sync_operations` (`status`, `nextAttemptAt`)"
        )
    }
}
