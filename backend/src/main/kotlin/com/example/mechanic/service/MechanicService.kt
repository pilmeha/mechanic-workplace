package com.example.mechanic.service

import com.example.mechanic.auth.JwtService
import com.example.mechanic.auth.PasswordHasher
import com.example.mechanic.db.Database
import com.example.mechanic.model.*
import com.example.mechanic.repository.*
import com.example.mechanic.onec.OneCClient
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

class MechanicService(
    private val db: Database,
    private val users: UserRepository,
    private val workOrders: WorkOrderRepository,
    private val sync: SyncRepository,
    private val mutations: MutationService,
    private val passwordHasher: PasswordHasher,
    private val jwtService: JwtService,
    private val json: Json
) {
    fun login(request: LoginRequest): TokenResponse {
        val user = users.findByLogin(request.login.trim())
            ?: throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Invalid login or password")
        if (!passwordHasher.verify(request.password, user.passwordHash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Invalid login or password")
        }
        val dto = user.toDto()
        return TokenResponse(jwtService.createToken(dto), jwtService.ttlSeconds, dto)
    }

    fun listWorkOrders(employeeId: UUID): WorkOrderListDto {
        val all = workOrders.listForEmployee(employeeId)
        val summaries = all.map { toSummary(it) }
        val current = summaries.firstOrNull { it.status == "IN_PROGRESS" }
        val upcoming = summaries.filter { it.id != current?.id }.take(20)
        return WorkOrderListDto(current = current, upcoming = upcoming)
    }

    fun getWorkOrder(employeeId: UUID, id: UUID): WorkOrderDetailsDto = db.connection { c ->
        val order = workOrders.findForEmployee(c, employeeId, id)
            ?: throw ApiException(HttpStatusCode.NotFound, "WORK_ORDER_NOT_FOUND", "Work order not found")
        val vehicle = workOrders.findVehicle(c, order.vehicleId)
            ?: throw ApiException(HttpStatusCode.InternalServerError, "VEHICLE_NOT_FOUND", "Vehicle referenced by work order was not found")
        WorkOrderDetailsDto(
            id = order.id.toString(),
            number = order.number,
            status = order.status,
            scheduledAt = order.scheduledAt.toString(),
            vehicle = vehicle.toDto(),
            customerName = order.customerName,
            complaint = order.complaint,
            operations = workOrders.listOperations(c, order.id).map { it.toDto() },
            issues = workOrders.listIssues(c, order.id).map { it.toDto() },
            attachments = workOrders.listAttachments(c, order.id).map { it.toDto() }
        )
    }

    fun startOperation(employeeId: UUID, workOrderId: UUID, operationId: UUID, idempotencyKey: String?): StoredResponse =
        mutations.execute(employeeId, idempotencyKey, "POST /work-orders/$workOrderId/operations/$operationId/start") { c ->
            ensureAssigned(c, employeeId, workOrderId)
            val current = workOrders.findOperation(c, workOrderId, operationId)
                ?: throw ApiException(HttpStatusCode.NotFound, "OPERATION_NOT_FOUND", "Operation not found")
            if (current.status == "COMPLETED") {
                throw ApiException(HttpStatusCode.Conflict, "OPERATION_ALREADY_COMPLETED", "Operation is already completed")
            }
            val updated = workOrders.updateOperationStart(c, operationId, Instant.now())
                ?: throw ApiException(HttpStatusCode.Conflict, "OPERATION_CANNOT_START", "Operation cannot be started")
            val payload = json.encodeToString(updated.toDto())
            sync.insertOutbox(c, "OPERATION_STARTED", updated.id, payload)
            json.encodeToString(StartOperationResponse(updated.toDto(), "PENDING"))
        }

    fun completeOperation(employeeId: UUID, workOrderId: UUID, operationId: UUID, request: CompleteOperationRequest, idempotencyKey: String?): StoredResponse =
        mutations.execute(employeeId, idempotencyKey, "POST /work-orders/$workOrderId/operations/$operationId/complete") { c ->
            ensureAssigned(c, employeeId, workOrderId)
            val current = workOrders.findOperation(c, workOrderId, operationId)
                ?: throw ApiException(HttpStatusCode.NotFound, "OPERATION_NOT_FOUND", "Operation not found")
            if (current.status == "COMPLETED") {
                throw ApiException(HttpStatusCode.Conflict, "OPERATION_ALREADY_COMPLETED", "Operation is already completed")
            }
            val updated = workOrders.updateOperationComplete(c, operationId, request.description, Instant.now())
                ?: throw ApiException(HttpStatusCode.Conflict, "OPERATION_CANNOT_COMPLETE", "Operation cannot be completed")
            val payload = json.encodeToString(updated.toDto())
            sync.insertOutbox(c, "OPERATION_COMPLETED", updated.id, payload)
            json.encodeToString(CompleteOperationResponse(updated.toDto(), "PENDING"))
        }

    fun createIssue(employeeId: UUID, workOrderId: UUID, request: CreateIssueRequest, idempotencyKey: String?): StoredResponse =
        mutations.execute(employeeId, idempotencyKey, "POST /work-orders/$workOrderId/issues") { c ->
            ensureAssigned(c, employeeId, workOrderId)
            if (request.description.isBlank()) {
                throw ApiException(HttpStatusCode.BadRequest, "DESCRIPTION_REQUIRED", "Issue description must not be blank")
            }
            request.operationId?.let {
                workOrders.findOperation(c, workOrderId, UUID.fromString(it))
                    ?: throw ApiException(HttpStatusCode.BadRequest, "OPERATION_NOT_FOUND", "Referenced operation does not belong to this work order")
            }
            val issue = IssueRecord(
                id = UUID.randomUUID(),
                oneCId = null,
                workOrderId = workOrderId,
                operationId = request.operationId?.let(UUID::fromString),
                description = request.description.trim(),
                additionalWorkRequired = request.additionalWorkRequired,
                createdAt = Instant.now()
            )
            workOrders.insertIssue(c, issue)
            sync.insertOutbox(c, "ISSUE_CREATED", issue.id, json.encodeToString(issue.toDto()))
            json.encodeToString(CreateIssueResponse(issue.toDto(), "PENDING"))
        }

    fun createAttachment(
        employeeId: UUID,
        workOrderId: UUID,
        operationId: UUID?,
        savedFile: SavedFile,
        contentType: String,
        idempotencyKey: String?
    ): StoredResponse {
        try {
            return mutations.execute(employeeId, idempotencyKey, "POST /work-orders/$workOrderId/attachments") { c ->
                ensureAssigned(c, employeeId, workOrderId)
                if (operationId != null) {
                    workOrders.findOperation(c, workOrderId, operationId)
                        ?: throw ApiException(HttpStatusCode.BadRequest, "OPERATION_NOT_FOUND", "Referenced operation does not belong to this work order")
                }
                val count = c.prepareStatement("SELECT COUNT(*) FROM attachments WHERE work_order_id = ?").use { ps ->
                    ps.setObject(1, workOrderId)
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
                if (count >= 20) {
                    throw ApiException(HttpStatusCode.BadRequest, "PHOTO_LIMIT_REACHED", "A work order may contain at most 20 photos")
                }
                val attachment = AttachmentRecord(
                    id = savedFile.id,
                    oneCId = null,
                    workOrderId = workOrderId,
                    operationId = operationId,
                    originalName = savedFile.originalName,
                    storedPath = savedFile.relativePath,
                    contentType = contentType,
                    sizeBytes = savedFile.sizeBytes,
                    syncStatus = "PENDING",
                    createdAt = Instant.now()
                )
                workOrders.insertAttachment(c, attachment)
                sync.insertOutbox(c, "ATTACHMENT_UPLOADED", attachment.id, json.encodeToString(attachment.toDto()))
                json.encodeToString(AttachmentUploadResponse(attachment.toDto(), "PENDING"))
            }
        } catch (t: Throwable) {
            runCatching { java.nio.file.Files.deleteIfExists(savedFile.absolutePath) }
            throw t
        }
    }

    fun getAttachment(employeeId: UUID, attachmentId: UUID): AttachmentRecord = db.connection { c ->
        workOrders.findAttachmentForEmployee(c, employeeId, attachmentId)
            ?: throw ApiException(HttpStatusCode.NotFound, "ATTACHMENT_NOT_FOUND", "Attachment not found")
    }

    fun syncStatus(): SyncStatusDto {
        val counts = sync.statusCounts()
        return SyncStatusDto(
            pending = counts["PENDING"] ?: 0,
            processing = counts["PROCESSING"] ?: 0,
            failed = counts["FAILED"] ?: 0,
            sent = counts["SENT"] ?: 0
        )
    }

    private fun ensureAssigned(c: java.sql.Connection, employeeId: UUID, workOrderId: UUID) {
        if (workOrders.findForEmployee(c, employeeId, workOrderId) == null) {
            throw ApiException(HttpStatusCode.NotFound, "WORK_ORDER_NOT_FOUND", "Work order not found")
        }
    }

    private fun toSummary(order: WorkOrderRecord): WorkOrderSummaryDto = db.connection { c ->
        val vehicle = workOrders.findVehicle(c, order.vehicleId)
            ?: throw ApiException(HttpStatusCode.InternalServerError, "VEHICLE_NOT_FOUND", "Vehicle referenced by work order was not found")
        WorkOrderSummaryDto(order.id.toString(), order.number, order.status, order.scheduledAt.toString(), vehicle.toDto())
    }

    private fun UserRecord.toDto() = UserDto(id.toString(), name, role, oneCEmployeeId)
    private fun VehicleRecord.toDto() = VehicleDto(id.toString(), vin, brand, model, year, mileage)
    private fun OperationRecord.toDto() = OperationDto(id.toString(), name, status, startedAt?.toString(), completedAt?.toString(), description)
    private fun IssueRecord.toDto() = IssueDto(id.toString(), description, additionalWorkRequired, operationId?.toString(), createdAt.toString())
    private fun AttachmentRecord.toDto() = AttachmentDto(id.toString(), originalName, contentType, sizeBytes, syncStatus, createdAt.toString(), "/api/v1/attachments/$id/file")
}
