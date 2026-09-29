package com.example.mechanicworkplace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderCacheDao {
    @Query("SELECT * FROM cached_work_orders ORDER BY isCurrent DESC, scheduledAt ASC")
    suspend fun getAll(): List<CachedWorkOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedWorkOrderEntity>)

    @Query("DELETE FROM cached_work_orders")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetail(item: CachedWorkOrderDetailsEntity)

    @Query("SELECT * FROM cached_work_order_details WHERE id = :id LIMIT 1")
    suspend fun getDetail(id: String): CachedWorkOrderDetailsEntity?
}

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status != 'PROCESSING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncQueueEntity?

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE workOrderId = :workOrderId AND status IN ('PENDING', 'PROCESSING', 'FAILED')")
    fun observeWorkOrderPendingCount(workOrderId: String): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE workOrderId = :workOrderId AND type = 'UPLOAD_ATTACHMENT' AND status IN ('PENDING', 'PROCESSING', 'FAILED') ORDER BY createdAt ASC")
    fun observePendingPhotos(workOrderId: String): Flow<List<SyncQueueEntity>>
}

@Dao
interface UserLocalDao {
    @Query("SELECT * FROM cached_work_orders LIMIT 1")
    suspend fun noop(): CachedWorkOrderEntity?
}
