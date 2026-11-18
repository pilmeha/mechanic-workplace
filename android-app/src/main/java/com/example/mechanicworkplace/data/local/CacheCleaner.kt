package com.example.mechanicworkplace.data.local

interface CacheCleaner {
    suspend fun clearAll()
}

/** Clears all user-specific Room caches plus the sync queue on logout. */
class RoomCacheCleaner(
    private val orderDao: OrderCacheDao,
    private val photoDao: PhotoCacheDao,
    private val catalogDao: CatalogCacheDao,
    private val syncDao: SyncOperationDao
) : CacheCleaner {
    override suspend fun clearAll() {
        orderDao.clearOrders()
        orderDao.clearAllDetails()
        photoDao.clearAll()
        catalogDao.clearStates()
        catalogDao.clearDefects()
        syncDao.clearAll()
    }
}
