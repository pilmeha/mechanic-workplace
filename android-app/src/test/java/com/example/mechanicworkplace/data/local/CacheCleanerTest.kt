package com.example.mechanicworkplace.data.local

import com.example.mechanicworkplace.sync.FakeCatalogCacheDao
import com.example.mechanicworkplace.sync.FakeOrderCacheDao
import com.example.mechanicworkplace.sync.FakePhotoCacheDao
import com.example.mechanicworkplace.sync.FakeSyncOperationDao
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CacheCleanerTest {

    @Test
    fun `clearAll removes orders details photos catalogs and queue`() = runTest {
        val orders = FakeOrderCacheDao().apply {
            orders["o1"] = CachedOrderEntity(
                "o1", "1", null, "s", "state", null, null, null, null, null, null, null,
                false, "{}", 1
            )
            details["o1"] = CachedOrderDetailsEntity("o1", false, "{}", 1)
        }
        val photos = FakePhotoCacheDao().apply {
            photos += CachedPhotoEntity("o1", "p1", "p.jpg", 1, "I", "2026", true, 1)
        }
        val catalog = FakeCatalogCacheDao().apply {
            states += CachedStateEntity("s", "state", 1, 1)
            defects += CachedDefectEntity("d", "defect", null, null, null, 1)
        }
        val sync = FakeSyncOperationDao().apply {
            items["op"] = SyncOperationEntity(
                id = "op", orderId = "o1", type = SyncOperationEntity.TYPE_POST_ORDER,
                createdAt = 1, updatedAt = 1
            )
        }

        RoomCacheCleaner(orders, photos, catalog, sync).clearAll()

        assertTrue(orders.orders.isEmpty())
        assertTrue(orders.details.isEmpty())
        assertTrue(photos.photos.isEmpty())
        assertTrue(catalog.states.isEmpty())
        assertTrue(catalog.defects.isEmpty())
        assertTrue(sync.items.isEmpty())
    }
}
