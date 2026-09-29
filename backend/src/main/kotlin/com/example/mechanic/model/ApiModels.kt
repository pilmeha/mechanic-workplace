package com.example.mechanic.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String? = null,
    val details: Map<String, String>? = null
)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val role: String,
    val oneCEmployeeId: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val expiresInSeconds: Long,
    val user: UserDto
)

@Serializable
data class VehicleDto(
    val id: String,
    val vin: String,
    val brand: String,
    val model: String,
    val year: Int? = null,
    val mileage: Int
)

@Serializable
data class OperationDto(
    val id: String,
    val name: String,
    val status: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val description: String? = null
)

@Serializable
data class IssueDto(
    val id: String,
    val description: String,
    val additionalWorkRequired: Boolean,
    val operationId: String? = null,
    val createdAt: String
)

@Serializable
data class AttachmentDto(
    val id: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val syncStatus: String,
    val createdAt: String,
    val downloadUrl: String
)

@Serializable
data class WorkOrderSummaryDto(
    val id: String,
    val number: String,
    val status: String,
    val scheduledAt: String,
    val vehicle: VehicleDto
)

@Serializable
data class WorkOrderDetailsDto(
    val id: String,
    val number: String,
    val status: String,
    val scheduledAt: String,
    val vehicle: VehicleDto,
    val customerName: String,
    val complaint: String? = null,
    val operations: List<OperationDto>,
    val issues: List<IssueDto>,
    val attachments: List<AttachmentDto>
)

@Serializable
data class WorkOrderListDto(
    val current: WorkOrderSummaryDto? = null,
    val upcoming: List<WorkOrderSummaryDto>
)

@Serializable
data class StartOperationResponse(
    val operation: OperationDto,
    val syncStatus: String
)

@Serializable
data class CompleteOperationRequest(
    val description: String? = null
)

@Serializable
data class CompleteOperationResponse(
    val operation: OperationDto,
    val syncStatus: String
)

@Serializable
data class CreateIssueRequest(
    val description: String,
    val additionalWorkRequired: Boolean = false,
    val operationId: String? = null
)

@Serializable
data class CreateIssueResponse(
    val issue: IssueDto,
    val syncStatus: String
)

@Serializable
data class AttachmentUploadResponse(
    val attachment: AttachmentDto,
    val syncStatus: String
)

@Serializable
data class SyncStatusDto(
    val pending: Int,
    val processing: Int,
    val failed: Int,
    val sent: Int
)
