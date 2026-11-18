package com.example.mechanicworkplace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(items: List<CachedOrderEntity>)

    @Query("SELECT * FROM cached_orders ORDER BY plannedDate ASC")
    suspend fun getAllOrders(): List<CachedOrderEntity>

    @Query(
        """
        SELECT * FROM cached_orders
        WHERE number LIKE '%' || :query || '%'
           OR IFNULL(vehicleVin, '') LIKE '%' || :query || '%'
        ORDER BY plannedDate ASC
        """
    )
    suspend fun searchOrders(query: String): List<CachedOrderEntity>

    @Query("DELETE FROM cached_orders")
    suspend fun clearOrders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetails(item: CachedOrderDetailsEntity)

    @Query("SELECT * FROM cached_order_details WHERE id = :id LIMIT 1")
    suspend fun getDetails(id: String): CachedOrderDetailsEntity?

    @Query("DELETE FROM cached_order_details WHERE id = :id")
    suspend fun deleteDetails(id: String)

    @Query("DELETE FROM cached_order_details")
    suspend fun clearAllDetails()
}

@Dao
interface PhotoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedPhotoEntity>)

    @Query("SELECT * FROM cached_photos WHERE orderId = :orderId ORDER BY uploadedAt ASC")
    suspend fun getForOrder(orderId: String): List<CachedPhotoEntity>

    @Query("DELETE FROM cached_photos WHERE orderId = :orderId AND photoId = :photoId")
    suspend fun delete(orderId: String, photoId: String)

    @Query("DELETE FROM cached_photos WHERE orderId = :orderId")
    suspend fun clearOrder(orderId: String)

    @Query("DELETE FROM cached_photos")
    suspend fun clearAll()
}

@Dao
interface CatalogCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStates(items: List<CachedStateEntity>)

    @Query("SELECT * FROM cached_states ORDER BY sortOrder ASC, name ASC")
    suspend fun getStates(): List<CachedStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefects(items: List<CachedDefectEntity>)

    @Query(
        """
        SELECT * FROM cached_defects
        WHERE name LIKE '%' || :query || '%'
           OR IFNULL(code, '') LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    suspend fun getDefects(query: String): List<CachedDefectEntity>

    @Query("DELETE FROM cached_defects")
    suspend fun clearDefects()

    @Query("DELETE FROM cached_states")
    suspend fun clearStates()
}

@Dao
interface SyncOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncOperationEntity?

    @Update
    suspend fun update(item: SyncOperationEntity)

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE status = 'PENDING' AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun eligible(now: Long, limit: Int): List<SyncOperationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM sync_operations
        WHERE orderId = :orderId AND type = 'SAVE_WORKS'
          AND status IN (:statuses)
        """
    )
    suspend fun unfinishedWorksCount(
        orderId: String,
        statuses: List<String> = SyncOperationEntity.UNFINISHED_WORKS_STATUSES
    ): Int

    @Query(
        """
        UPDATE sync_operations
        SET status = 'NEEDS_RECONCILIATION', lastError = 'stale SENDING',
            updatedAt = :now
        WHERE status = 'SENDING' AND startedAt IS NOT NULL AND startedAt < :cutoff
        """
    )
    suspend fun resetStaleSending(cutoff: Long, now: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET status = 'PENDING', lastError = 'reconcile requested',
            nextAttemptAt = :now, updatedAt = :now
        WHERE status = 'NEEDS_RECONCILIATION'
          AND (:orderId IS NULL OR orderId = :orderId)
        """
    )
    suspend fun requestReconciliation(orderId: String?, now: Long): Int

    @Query(
        """
        UPDATE sync_operations
        SET status = 'PENDING', nextAttemptAt = :now, updatedAt = :now
        WHERE status = 'NEEDS_AUTH'
        """
    )
    suspend fun resetNeedsAuthToPending(now: Long): Int

    @Query("SELECT MIN(nextAttemptAt) FROM sync_operations WHERE status = 'PENDING'")
    suspend fun nextPendingAt(): Long?

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_operations
        WHERE status IN ('PENDING', 'SENDING', 'NEEDS_RECONCILIATION',
                         'NEEDS_AUTH', 'FAILED')
        """
    )
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT status, COUNT(*) AS count FROM sync_operations GROUP BY status")
    fun observeCounts(): Flow<List<StatusCount>>

    @Query("DELETE FROM sync_operations")
    suspend fun clearAll()
}

data class StatusCount(val status: String, val count: Int)
