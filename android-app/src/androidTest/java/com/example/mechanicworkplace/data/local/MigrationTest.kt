package com.example.mechanicworkplace.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real v1 -> v2 migration on an Android device/emulator.
 *
 * Note: MigrationTestHelper requires an exported v1 schema; v1 was never exported, so this
 * test creates the v1 tables explicitly and opens Room v2 with MIGRATION_1_2. That exercises
 * the same migration path and Room schema validation.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        val legacy = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_work_orders` (`id` TEXT NOT NULL, `number` TEXT NOT NULL, `status` TEXT NOT NULL, `scheduledAt` TEXT NOT NULL, `vehicleJson` TEXT NOT NULL, `isCurrent` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_work_order_details` (`id` TEXT NOT NULL, `json` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_queue` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `workOrderId` TEXT NOT NULL, `operationId` TEXT, `payloadJson` TEXT, `localFilePath` TEXT, `fileName` TEXT, `mimeType` TEXT, `idempotencyKey` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        legacy.execSQL("INSERT INTO sync_queue(id,type,workOrderId,idempotencyKey,status,attempts,createdAt) VALUES('old','START_OPERATION','wo','k','PENDING',0,1)")
        legacy.version = 1
        legacy.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migratesFromV1AndDropsLegacyQueue() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val tables = mutableSetOf<String>()
        db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'")
            .use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }

        assertTrue("cached_orders" in tables)
        assertTrue("sync_operations" in tables)
        assertTrue("sync_queue" !in tables)
        assertTrue("cached_work_orders" !in tables)
        db.syncOperationDao().insert(
            SyncOperationEntity(
                id = "new",
                orderId = "o",
                type = SyncOperationEntity.TYPE_POST_ORDER,
                createdAt = 1,
                updatedAt = 1
            )
        )
        db.close()
    }
}
