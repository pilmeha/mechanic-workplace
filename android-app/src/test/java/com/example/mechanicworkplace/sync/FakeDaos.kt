package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.CachedOrderDetailsEntity
import com.example.mechanicworkplace.data.local.CachedOrderEntity
import com.example.mechanicworkplace.data.local.CachedPhotoEntity
import com.example.mechanicworkplace.data.local.CatalogCacheDao
import com.example.mechanicworkplace.data.local.CachedDefectEntity
import com.example.mechanicworkplace.data.local.CachedStateEntity
import com.example.mechanicworkplace.data.local.OrderCacheDao
import com.example.mechanicworkplace.data.local.PhotoCacheDao
import com.example.mechanicworkplace.data.local.StatusCount
import com.example.mechanicworkplace.data.local.SyncOperationDao
import com.example.mechanicworkplace.data.local.SyncOperationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSyncOperationDao : SyncOperationDao {
    val items = LinkedHashMap<String, SyncOperationEntity>()

    override suspend fun insert(item: SyncOperationEntity) {
        items[item.id] = item
    }

    override suspend fun getById(id: String): SyncOperationEntity? = items[id]

    override suspend fun update(item: SyncOperationEntity) {
        items[item.id] = item
    }

    override suspend fun eligible(now: Long, limit: Int): List<SyncOperationEntity> =
        items.values
            .filter { it.status == SyncOperationEntity.STATUS_PENDING && it.nextAttemptAt <= now }
            .sortedBy { it.createdAt }
            .take(limit)

    override suspend fun unfinishedWorksCount(
        orderId: String,
        statuses: List<String>
    ): Int = items.values.count {
        it.orderId == orderId &&
            it.type == SyncOperationEntity.TYPE_SAVE_WORKS &&
            it.status in statuses
    }

    override suspend fun resetStaleSending(cutoff: Long, now: Long): Int {
        var count = 0
        items.values.filter {
            it.status == SyncOperationEntity.STATUS_SENDING &&
                it.startedAt != null && it.startedAt < cutoff
        }.forEach {
            items[it.id] = it.copy(
                status = SyncOperationEntity.STATUS_NEEDS_RECONCILIATION,
                lastError = "stale SENDING",
                updatedAt = now
            )
            count++
        }
        return count
    }

    override suspend fun requestReconciliation(orderId: String?, now: Long): Int {
        var count = 0
        items.values.filter {
            it.status == SyncOperationEntity.STATUS_NEEDS_RECONCILIATION &&
                (orderId == null || it.orderId == orderId)
        }.forEach {
            items[it.id] = it.copy(
                status = SyncOperationEntity.STATUS_PENDING,
                lastError = "reconcile requested",
                nextAttemptAt = now,
                updatedAt = now
            )
            count++
        }
        return count
    }

    override suspend fun resetNeedsAuthToPending(now: Long): Int {
        var count = 0
        items.values.filter {
            it.status == SyncOperationEntity.STATUS_NEEDS_AUTH
        }.forEach {
            items[it.id] = it.copy(
                status = SyncOperationEntity.STATUS_PENDING,
                nextAttemptAt = now,
                updatedAt = now
            )
            count++
        }
        return count
    }

    override suspend fun nextPendingAt(): Long? =
        items.values.filter { it.status == SyncOperationEntity.STATUS_PENDING }
            .minOfOrNull { it.nextAttemptAt }

    override suspend fun pendingCount(): Int =
        items.values.count { it.status == SyncOperationEntity.STATUS_PENDING }

    override fun observeActiveCount(): Flow<Int> = MutableStateFlow(0)

    override fun observeCounts(): Flow<List<StatusCount>> = MutableStateFlow(emptyList())

    override suspend fun clearAll() = items.clear()
}

class FakeOrderCacheDao : OrderCacheDao {
    val orders = LinkedHashMap<String, CachedOrderEntity>()
    val details = LinkedHashMap<String, CachedOrderDetailsEntity>()

    override suspend fun upsertOrders(items: List<CachedOrderEntity>) {
        items.forEach { orders[it.id] = it }
    }

    override suspend fun getAllOrders(): List<CachedOrderEntity> = orders.values.toList()

    override suspend fun searchOrders(query: String): List<CachedOrderEntity> =
        orders.values.filter { it.number.contains(query) || it.vehicleVin?.contains(query) == true }

    override suspend fun clearOrders() = orders.clear()

    override suspend fun upsertDetails(item: CachedOrderDetailsEntity) {
        details[item.id] = item
    }

    override suspend fun getDetails(id: String): CachedOrderDetailsEntity? = details[id]

    override suspend fun deleteDetails(id: String) {
        details.remove(id)
    }

    override suspend fun clearAllDetails() = details.clear()
}

class FakePhotoCacheDao : PhotoCacheDao {
    val photos = mutableListOf<CachedPhotoEntity>()

    override suspend fun upsertAll(items: List<CachedPhotoEntity>) {
        items.forEach { photo ->
            photos.removeAll { it.orderId == photo.orderId && it.photoId == photo.photoId }
            photos += photo
        }
    }

    override suspend fun getForOrder(orderId: String): List<CachedPhotoEntity> =
        photos.filter { it.orderId == orderId }

    override suspend fun delete(orderId: String, photoId: String) {
        photos.removeAll { it.orderId == orderId && it.photoId == photoId }
    }

    override suspend fun clearOrder(orderId: String) {
        photos.removeAll { it.orderId == orderId }
    }

    override suspend fun clearAll() = photos.clear()
}


class FakeCatalogCacheDao : CatalogCacheDao {
    val states = mutableListOf<CachedStateEntity>()
    val defects = mutableListOf<CachedDefectEntity>()

    override suspend fun upsertStates(items: List<CachedStateEntity>) {
        states += items
    }

    override suspend fun getStates(): List<CachedStateEntity> = states.toList()

    override suspend fun upsertDefects(items: List<CachedDefectEntity>) {
        defects += items
    }

    override suspend fun getDefects(query: String): List<CachedDefectEntity> =
        defects.filter { it.name.contains(query) || it.code?.contains(query) == true }

    override suspend fun clearDefects() = defects.clear()

    override suspend fun clearStates() = states.clear()
}
