package com.example.mechanicworkplace.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_orders",
    indices = [
        Index("stateName"),
        Index("plannedDate"),
        Index("number"),
        Index("vehicleVin")
    ]
)
data class CachedOrderEntity(
    @PrimaryKey val id: String,
    val number: String,
    val date: String?,
    val stateCode: String,
    val stateName: String,
    val clientId: String?,
    val clientName: String?,
    val vehicleId: String?,
    val vehicleModel: String?,
    val vehicleVin: String?,
    val plannedDate: String?,
    val sumWorks: String?,
    val posted: Boolean,
    val json: String,
    val updatedAt: Long
)

@Entity(tableName = "cached_order_details")
data class CachedOrderDetailsEntity(
    @PrimaryKey val id: String,
    val posted: Boolean,
    val json: String,
    val updatedAt: Long
)

@Entity(
    tableName = "cached_photos",
    primaryKeys = ["orderId", "photoId"],
    indices = [Index("orderId")]
)
data class CachedPhotoEntity(
    val orderId: String,
    val photoId: String,
    val fileName: String,
    val size: Long,
    val uploadedBy: String?,
    val uploadedAt: String?,
    val canDelete: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "cached_states")
data class CachedStateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int?,
    val updatedAt: Long
)

@Entity(
    tableName = "cached_defects",
    indices = [Index("name"), Index("code")]
)
data class CachedDefectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String?,
    val type: String?,
    val normHour: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["orderId", "type", "status", "createdAt"]),
        Index(value = ["status", "nextAttemptAt"])
    ]
)
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val type: String,
    val status: String = STATUS_PENDING,
    val payloadJson: String? = null,
    val localFilePath: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val baselineJson: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    val nextAttemptAt: Long = 0,
    val startedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val TYPE_SAVE_WORKS = "SAVE_WORKS"
        const val TYPE_POST_ORDER = "POST_ORDER"
        const val TYPE_ADD_DEFECT = "ADD_DEFECT"
        const val TYPE_UPLOAD_PHOTO = "UPLOAD_PHOTO"
        const val TYPE_DELETE_PHOTO = "DELETE_PHOTO"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_NEEDS_RECONCILIATION = "NEEDS_RECONCILIATION"
        const val STATUS_NEEDS_AUTH = "NEEDS_AUTH"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DONE = "DONE"

        val ACTIVE_STATUSES = listOf(
            STATUS_PENDING,
            STATUS_SENDING,
            STATUS_NEEDS_AUTH
        )

        val UNFINISHED_WORKS_STATUSES = listOf(
            STATUS_PENDING,
            STATUS_SENDING,
            STATUS_NEEDS_RECONCILIATION,
            STATUS_FAILED
        )
    }
}
