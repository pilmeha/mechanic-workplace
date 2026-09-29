package com.example.mechanic.model

import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val login: String,
    val passwordHash: String,
    val name: String,
    val role: String,
    val oneCEmployeeId: String
)

data class WorkOrderRecord(
    val id: UUID,
    val oneCId: String?,
    val number: String,
    val status: String,
    val vehicleId: UUID,
    val customerName: String,
    val complaint: String?,
    val scheduledAt: Instant
)

data class VehicleRecord(
    val id: UUID,
    val vin: String,
    val brand: String,
    val model: String,
    val year: Int?,
    val mileage: Int
)

data class OperationRecord(
    val id: UUID,
    val oneCId: String?,
    val workOrderId: UUID,
    val name: String,
    val status: String,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val description: String?
)

data class IssueRecord(
    val id: UUID,
    val oneCId: String?,
    val workOrderId: UUID,
    val operationId: UUID?,
    val description: String,
    val additionalWorkRequired: Boolean,
    val createdAt: Instant
)

data class AttachmentRecord(
    val id: UUID,
    val oneCId: String?,
    val workOrderId: UUID,
    val operationId: UUID?,
    val originalName: String,
    val storedPath: String,
    val contentType: String,
    val sizeBytes: Long,
    val syncStatus: String,
    val createdAt: Instant
)

data class OutboxEvent(
    val id: UUID,
    val eventType: String,
    val aggregateId: UUID,
    val payload: String,
    val status: String,
    val attempts: Int,
    val availableAt: Instant,
    val lockedAt: Instant?
)
