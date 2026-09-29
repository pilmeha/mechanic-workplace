package com.example.mechanic.repository

import com.example.mechanic.db.Database
import com.example.mechanic.model.*
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class WorkOrderRepository(private val db: Database) {
    fun listForEmployee(employeeId: UUID): List<WorkOrderRecord> = db.connection { c ->
        c.prepareStatement("""
            SELECT id, onec_id, number, status, vehicle_id, customer_name, complaint, scheduled_at
            FROM work_orders
            WHERE assigned_employee_id = ?
            ORDER BY scheduled_at ASC
        """.trimIndent()).use { ps ->
            ps.setObject(1, employeeId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWorkOrder()) } }
        }
    }

    fun findForEmployee(c: Connection, employeeId: UUID, workOrderId: UUID): WorkOrderRecord? =
        c.prepareStatement("""
            SELECT id, onec_id, number, status, vehicle_id, customer_name, complaint, scheduled_at
            FROM work_orders
            WHERE id = ? AND assigned_employee_id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, workOrderId)
            ps.setObject(2, employeeId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toWorkOrder() else null }
        }

    fun findById(c: Connection, workOrderId: UUID): WorkOrderRecord? =
        c.prepareStatement("""
            SELECT id, onec_id, number, status, vehicle_id, customer_name, complaint, scheduled_at
            FROM work_orders WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, workOrderId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toWorkOrder() else null }
        }

    fun findVehicle(c: Connection, vehicleId: UUID): VehicleRecord? =
        c.prepareStatement("""
            SELECT id, vin, brand, model, year, mileage FROM vehicles WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, vehicleId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else VehicleRecord(
                    id = rs.getObject("id", UUID::class.java),
                    vin = rs.getString("vin"),
                    brand = rs.getString("brand"),
                    model = rs.getString("model"),
                    year = rs.getObject("year")?.let { (it as Number).toInt() },
                    mileage = rs.getInt("mileage")
                )
            }
        }

    fun listOperations(c: Connection, workOrderId: UUID): List<OperationRecord> =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, name, status, started_at, completed_at, description
            FROM work_order_operations WHERE work_order_id = ? ORDER BY name
        """.trimIndent()).use { ps ->
            ps.setObject(1, workOrderId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toOperation()) } }
        }

    fun findOperation(c: Connection, workOrderId: UUID, operationId: UUID): OperationRecord? =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, name, status, started_at, completed_at, description
            FROM work_order_operations WHERE id = ? AND work_order_id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, operationId)
            ps.setObject(2, workOrderId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toOperation() else null }
        }

    fun listIssues(c: Connection, workOrderId: UUID): List<IssueRecord> =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, operation_id, description, additional_work_required, created_at
            FROM issues WHERE work_order_id = ? ORDER BY created_at DESC
        """.trimIndent()).use { ps ->
            ps.setObject(1, workOrderId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toIssue()) } }
        }

    fun listAttachments(c: Connection, workOrderId: UUID): List<AttachmentRecord> =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, operation_id, original_name, stored_path,
                   content_type, size_bytes, sync_status, created_at
            FROM attachments WHERE work_order_id = ? ORDER BY created_at
        """.trimIndent()).use { ps ->
            ps.setObject(1, workOrderId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toAttachment()) } }
        }

    fun updateOperationStart(c: Connection, operationId: UUID, startedAt: Instant): OperationRecord? {
        c.prepareStatement("""
            UPDATE work_order_operations
            SET status = 'IN_PROGRESS', started_at = COALESCE(started_at, ?)
            WHERE id = ? AND status IN ('ASSIGNED', 'IN_PROGRESS')
        """.trimIndent()).use { ps ->
            ps.setTimestamp(1, Timestamp.from(startedAt))
            ps.setObject(2, operationId)
            ps.executeUpdate()
        }
        return findOperationById(c, operationId)
    }

    fun updateOperationComplete(c: Connection, operationId: UUID, description: String?, completedAt: Instant): OperationRecord? {
        c.prepareStatement("""
            UPDATE work_order_operations
            SET status = 'COMPLETED', completed_at = COALESCE(completed_at, ?),
                description = COALESCE(?, description)
            WHERE id = ? AND status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED')
        """.trimIndent()).use { ps ->
            ps.setTimestamp(1, Timestamp.from(completedAt))
            ps.setString(2, description)
            ps.setObject(3, operationId)
            ps.executeUpdate()
        }
        return findOperationById(c, operationId)
    }


    fun findIssueById(c: Connection, issueId: UUID): IssueRecord? =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, operation_id, description, additional_work_required, created_at
            FROM issues WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, issueId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toIssue() else null }
        }

    fun findAttachmentById(c: Connection, attachmentId: UUID): AttachmentRecord? =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, operation_id, original_name, stored_path,
                   content_type, size_bytes, sync_status, created_at
            FROM attachments WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, attachmentId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toAttachment() else null }
        }

    fun insertIssue(c: Connection, issue: IssueRecord) {
        c.prepareStatement("""
            INSERT INTO issues(id, onec_id, work_order_id, operation_id, description, additional_work_required, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { ps ->
            ps.setObject(1, issue.id)
            ps.setString(2, issue.oneCId)
            ps.setObject(3, issue.workOrderId)
            ps.setObject(4, issue.operationId)
            ps.setString(5, issue.description)
            ps.setBoolean(6, issue.additionalWorkRequired)
            ps.setTimestamp(7, Timestamp.from(issue.createdAt))
            ps.executeUpdate()
        }
    }

    fun insertAttachment(c: Connection, attachment: AttachmentRecord) {
        c.prepareStatement("""
            INSERT INTO attachments(
                id, onec_id, work_order_id, operation_id, original_name, stored_path,
                content_type, size_bytes, sync_status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { ps ->
            ps.setObject(1, attachment.id)
            ps.setString(2, attachment.oneCId)
            ps.setObject(3, attachment.workOrderId)
            ps.setObject(4, attachment.operationId)
            ps.setString(5, attachment.originalName)
            ps.setString(6, attachment.storedPath)
            ps.setString(7, attachment.contentType)
            ps.setLong(8, attachment.sizeBytes)
            ps.setString(9, attachment.syncStatus)
            ps.setTimestamp(10, Timestamp.from(attachment.createdAt))
            ps.executeUpdate()
        }
    }

    fun findAttachmentForEmployee(c: Connection, employeeId: UUID, attachmentId: UUID): AttachmentRecord? =
        c.prepareStatement("""
            SELECT a.id, a.onec_id, a.work_order_id, a.operation_id, a.original_name, a.stored_path,
                   a.content_type, a.size_bytes, a.sync_status, a.created_at
            FROM attachments a
            JOIN work_orders w ON w.id = a.work_order_id
            WHERE a.id = ? AND w.assigned_employee_id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, attachmentId)
            ps.setObject(2, employeeId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toAttachment() else null }
        }

    fun setAttachmentSyncStatus(c: Connection, attachmentId: UUID, status: String, oneCId: String? = null) {
        c.prepareStatement("""
            UPDATE attachments SET sync_status = ?, onec_id = COALESCE(?, onec_id) WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setString(1, status)
            ps.setString(2, oneCId)
            ps.setObject(3, attachmentId)
            ps.executeUpdate()
        }
    }

    fun findOperationById(c: Connection, operationId: UUID): OperationRecord? =
        c.prepareStatement("""
            SELECT id, onec_id, work_order_id, name, status, started_at, completed_at, description
            FROM work_order_operations WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, operationId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toOperation() else null }
        }

    private fun ResultSet.toWorkOrder() = WorkOrderRecord(
        id = getObject("id", UUID::class.java),
        oneCId = getString("onec_id"),
        number = getString("number"),
        status = getString("status"),
        vehicleId = getObject("vehicle_id", UUID::class.java),
        customerName = getString("customer_name"),
        complaint = getString("complaint"),
        scheduledAt = getTimestamp("scheduled_at").toInstant()
    )

    private fun ResultSet.toOperation() = OperationRecord(
        id = getObject("id", UUID::class.java),
        oneCId = getString("onec_id"),
        workOrderId = getObject("work_order_id", UUID::class.java),
        name = getString("name"),
        status = getString("status"),
        startedAt = getTimestamp("started_at")?.toInstant(),
        completedAt = getTimestamp("completed_at")?.toInstant(),
        description = getString("description")
    )

    private fun ResultSet.toIssue() = IssueRecord(
        id = getObject("id", UUID::class.java),
        oneCId = getString("onec_id"),
        workOrderId = getObject("work_order_id", UUID::class.java),
        operationId = getObject("operation_id", UUID::class.java),
        description = getString("description"),
        additionalWorkRequired = getBoolean("additional_work_required"),
        createdAt = getTimestamp("created_at").toInstant()
    )

    private fun ResultSet.toAttachment() = AttachmentRecord(
        id = getObject("id", UUID::class.java),
        oneCId = getString("onec_id"),
        workOrderId = getObject("work_order_id", UUID::class.java),
        operationId = getObject("operation_id", UUID::class.java),
        originalName = getString("original_name"),
        storedPath = getString("stored_path"),
        contentType = getString("content_type"),
        sizeBytes = getLong("size_bytes"),
        syncStatus = getString("sync_status"),
        createdAt = getTimestamp("created_at").toInstant()
    )
}
