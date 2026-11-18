package com.example.mechanicworkplace.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class MigrationTest {

    @Test
    fun `migration drops old JWT caches and queue then creates v2 tables`() {
        val executed = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                executed += args[0] as String
            }
            null
        } as SupportSQLiteDatabase

        MIGRATION_1_2.migrate(database)

        assertTrue(executed.any { it.contains("DROP TABLE IF EXISTS `sync_queue`") })
        assertTrue(executed.any { it.contains("DROP TABLE IF EXISTS `cached_work_orders`") })
        assertTrue(executed.any { it.contains("DROP TABLE IF EXISTS `cached_work_order_details`") })

        val created = setOf(
            "cached_orders",
            "cached_order_details",
            "cached_photos",
            "cached_states",
            "cached_defects",
            "sync_operations"
        )
        created.forEach { table ->
            assertTrue(
                executed.any { sql -> sql.contains("CREATE TABLE IF NOT EXISTS `$table`") },
                "missing CREATE TABLE for $table"
            )
        }
        assertFalse(executed.any { it.contains("INSERT INTO `sync_queue`") })
    }
}
