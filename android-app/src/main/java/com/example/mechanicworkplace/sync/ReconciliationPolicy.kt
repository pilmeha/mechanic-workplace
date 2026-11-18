package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.SyncOperationEntity
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.DefectDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorkRowDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import java.math.BigDecimal

enum class SyncDecision {
    DONE,
    RETRY,
    NEEDS_RECONCILIATION,
    FAILED
}

/**
 * Pure reconciliation decisions for "unknown outcome" cases.
 * Evidence = a fresh GET /orders/{id} (or photos list). `null` means the evidence request failed.
 */
object ReconciliationPolicy {
    const val BASELINE_TTL_MS = 24 * 60 * 60 * 1000L

    fun postOrder(posted: Boolean?): SyncDecision = when (posted) {
        true -> SyncDecision.DONE
        false -> SyncDecision.RETRY
        null -> SyncDecision.NEEDS_RECONCILIATION
    }

    fun saveWorks(expected: SaveWorksRequest, server: OrderCardDto?): SyncDecision {
        if (server == null) return SyncDecision.NEEDS_RECONCILIATION
        val expectedByLine = expected.works.associateBy { it.lineId }
        if (expectedByLine.keys.any { it == null } || expectedByLine.size != expected.works.size) {
            return SyncDecision.NEEDS_RECONCILIATION
        }
        val serverByLine = server.works.associateBy { it.lineId }
        if (serverByLine.keys != expectedByLine.keys) return SyncDecision.NEEDS_RECONCILIATION

        for ((lineId, row) in expectedByLine) {
            val serverRow = serverByLine[lineId] ?: return SyncDecision.NEEDS_RECONCILIATION
            if (serverRow.work.id != row.workId) return SyncDecision.NEEDS_RECONCILIATION
            if (!decimalEquals(row.quantity, serverRow.quantity)) return SyncDecision.NEEDS_RECONCILIATION
            if (!decimalEquals(row.price, serverRow.price)) return SyncDecision.NEEDS_RECONCILIATION
            if (row.note.orEmpty() != serverRow.note.orEmpty()) return SyncDecision.NEEDS_RECONCILIATION
            if (!executorsMatch(row, serverRow)) return SyncDecision.NEEDS_RECONCILIATION
        }
        return SyncDecision.DONE
    }

    fun addDefect(
        request: AddDefectRequest,
        beforeCount: Int,
        server: OrderCardDto?
    ): SyncDecision {
        if (server == null) return SyncDecision.NEEDS_RECONCILIATION
        val after = server.defects.count { it.matches(request) }
        return when {
            after == beforeCount + 1 -> SyncDecision.DONE
            after == beforeCount -> SyncDecision.RETRY
            else -> SyncDecision.NEEDS_RECONCILIATION
        }
    }

    fun deletePhoto(photoStillPresent: Boolean?): SyncDecision = when (photoStillPresent) {
        false -> SyncDecision.DONE
        true -> SyncDecision.RETRY
        null -> SyncDecision.NEEDS_RECONCILIATION
    }

    /** Upload may be retried because the server deduplicates by SHA-256. */
    fun uploadPhotoUnknownOutcome(): SyncDecision = SyncDecision.RETRY

    fun baselineExpired(operation: SyncOperationEntity, now: Long): Boolean =
        operation.type in setOf(
            SyncOperationEntity.TYPE_SAVE_WORKS,
            SyncOperationEntity.TYPE_ADD_DEFECT
        ) && now - operation.createdAt > BASELINE_TTL_MS

    private fun decimalEquals(expected: BigDecimal?, actual: BigDecimal?): Boolean =
        if (expected == null || actual == null) {
            expected == null && actual == null
        } else {
            expected.compareTo(actual) == 0
        }

    private fun executorsMatch(
        expected: SaveWorkRowDto,
        server: com.example.mechanicworkplace.data.remote.onecmobile.WorkDto
    ): Boolean {
        val expectedExecutors = expected.executors.orEmpty()
        val serverExecutors = server.executors
        if (expectedExecutors.size != serverExecutors.size) return false
        val serverByEmployee = serverExecutors.associateBy { it.employeeId }
        return expectedExecutors.all { executor ->
            val serverExecutor = serverByEmployee[executor.employeeId] ?: return@all false
            executor.percent == null || serverExecutor.percent == executor.percent
        }
    }

    private fun DefectDto.matches(request: AddDefectRequest): Boolean {
        if (reasonId != request.reasonId) return false
        if (content?.trim().orEmpty() != request.content?.trim().orEmpty()) return false
        val left = normHour
        val right = request.normHour
        return if (left == null || right == null) {
            left == null && right == null
        } else {
            left.compareTo(right) == 0
        }
    }
}

object BackoffPolicy {
    const val BASE_MS = 30_000L
    const val MAX_MS = 3_600_000L
    const val MAX_ATTEMPTS = 5

    fun delayMillis(attempts: Int): Long {
        val exponent = (attempts - 1).coerceAtLeast(0).coerceAtMost(10)
        return (BASE_MS shl exponent).coerceAtMost(MAX_MS)
    }

    fun shouldFail(attempts: Int): Boolean = attempts >= MAX_ATTEMPTS
}
