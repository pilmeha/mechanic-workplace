package com.example.mechanicworkplace.sync

import com.example.mechanicworkplace.data.local.SyncOperationEntity
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.SaveExecutorDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorkRowDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.testutil.testOrderCard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ReconciliationPolicyTest {

    private val expectedWorks = SaveWorksRequest(
        works = listOf(
            SaveWorkRowDto(
                lineId = "line-1",
                workId = "work-1",
                quantity = BigDecimal("2"),
                price = BigDecimal("1500.00"),
                note = "заменено",
                executors = listOf(SaveExecutorDto("employee-1", 100))
            )
        )
    )

    private val defectRequest = AddDefectRequest(
        reasonId = "reason-1",
        content = "Течь масла",
        normHour = BigDecimal("0.5")
    )

    @Test
    fun `post decisions`() {
        assertEquals(SyncDecision.DONE, ReconciliationPolicy.postOrder(true))
        assertEquals(SyncDecision.RETRY, ReconciliationPolicy.postOrder(false))
        assertEquals(
            SyncDecision.NEEDS_RECONCILIATION,
            ReconciliationPolicy.postOrder(null)
        )
    }

    @Test
    fun `saveWorks equal is done`() {
        assertEquals(
            SyncDecision.DONE,
            ReconciliationPolicy.saveWorks(expectedWorks, testOrderCard())
        )
    }

    @Test
    fun `saveWorks mismatch or missing card needs reconciliation`() {
        val changed = testOrderCard().copy(
            works = testOrderCard().works.map { it.copy(price = BigDecimal("1600.00")) }
        )
        assertEquals(
            SyncDecision.NEEDS_RECONCILIATION,
            ReconciliationPolicy.saveWorks(expectedWorks, changed)
        )
        assertEquals(
            SyncDecision.NEEDS_RECONCILIATION,
            ReconciliationPolicy.saveWorks(expectedWorks, null)
        )
    }

    @Test
    fun `saveWorks executor mismatch needs reconciliation`() {
        val changed = testOrderCard().copy(
            works = testOrderCard().works.map {
                it.copy(executors = it.executors.map { e -> e.copy(percent = 50) })
            }
        )
        assertEquals(
            SyncDecision.NEEDS_RECONCILIATION,
            ReconciliationPolicy.saveWorks(expectedWorks, changed)
        )
    }

    @Test
    fun `addDefect count decisions`() {
        val card = testOrderCard()
        assertEquals(
            SyncDecision.DONE,
            ReconciliationPolicy.addDefect(defectRequest, beforeCount = 0, server = card)
        )
        assertEquals(
            SyncDecision.RETRY,
            ReconciliationPolicy.addDefect(defectRequest, beforeCount = 1, server = card)
        )
        assertEquals(
            SyncDecision.NEEDS_RECONCILIATION,
            ReconciliationPolicy.addDefect(defectRequest, beforeCount = 0, server = null)
        )
    }

    @Test
    fun `delete photo decisions`() {
        assertEquals(SyncDecision.DONE, ReconciliationPolicy.deletePhoto(false))
        assertEquals(SyncDecision.RETRY, ReconciliationPolicy.deletePhoto(true))
        assertEquals(
            SyncDecision.NEEDS_RECONCILIATION,
            ReconciliationPolicy.deletePhoto(null)
        )
    }

    @Test
    fun `baseline expires after 24h only for save and defect`() {
        val now = 1_000_000_000L
        val old = now - ReconciliationPolicy.BASELINE_TTL_MS - 1
        val save = operation("SAVE_WORKS", old)
        val defect = operation("ADD_DEFECT", old)
        val post = operation("POST_ORDER", old)

        assertTrue(ReconciliationPolicy.baselineExpired(save, now))
        assertTrue(ReconciliationPolicy.baselineExpired(defect, now))
        assertFalse(ReconciliationPolicy.baselineExpired(post, now))
    }

    @Test
    fun `backoff is exponential and capped`() {
        assertEquals(30_000L, BackoffPolicy.delayMillis(1))
        assertEquals(60_000L, BackoffPolicy.delayMillis(2))
        assertEquals(480_000L, BackoffPolicy.delayMillis(5))
        assertEquals(3_600_000L, BackoffPolicy.delayMillis(10))
        assertTrue(BackoffPolicy.shouldFail(5))
        assertFalse(BackoffPolicy.shouldFail(4))
    }

    private fun operation(type: String, createdAt: Long) = SyncOperationEntity(
        id = "op",
        orderId = "order-1",
        type = type,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
