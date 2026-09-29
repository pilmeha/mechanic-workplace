package com.example.mechanicworkplace.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_work_orders")
data class CachedWorkOrderEntity(
    @PrimaryKey val id: String,
    val number: String,
    val status: String,
    val scheduledAt: String,
    val vehicleJson: String,
    val isCurrent: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_work_order_details")
data class CachedWorkOrderDetailsEntity(
    @PrimaryKey val id: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val type: String,
    val workOrderId: String,
    val operationId: String? = null,
    val payloadJson: String? = null,
    val localFilePath: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val idempotencyKey: String,
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_START_OPERATION = "START_OPERATION"
        const val TYPE_COMPLETE_OPERATION = "COMPLETE_OPERATION"
        const val TYPE_CREATE_ISSUE = "CREATE_ISSUE"
        const val TYPE_UPLOAD_ATTACHMENT = "UPLOAD_ATTACHMENT"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_FAILED = "FAILED"
    }
}
