package com.example.mechanic.outbox

import com.example.mechanic.config.AppConfig
import com.example.mechanic.db.Database
import com.example.mechanic.model.OutboxEvent
import com.example.mechanic.onec.OneCClient
import com.example.mechanic.onec.OneCResult
import com.example.mechanic.repository.SyncRepository
import com.example.mechanic.repository.WorkOrderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Path

class OutboxWorker(
    private val config: AppConfig,
    private val db: Database,
    private val sync: SyncRepository,
    private val workOrders: WorkOrderRepository,
    private val oneC: OneCClient
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                runCatching { sync.resetStaleProcessing(); processBatch() }
                    .onFailure { System.err.println("Outbox worker error: ${it.message}") }
                delay(config.syncIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun processBatch() {
        val events = sync.claimBatch(20)
        events.forEach { event ->
            runCatching { deliver(event) }
                .onSuccess { result ->
                    if (result.success) {
                        println("1C sync SUCCESS: event=${event.id} type=${event.eventType} externalId=${result.externalId ?: "-"}")
                        sync.markSent(event.id)
                        if (event.eventType == "ATTACHMENT_UPLOADED") {
                            db.connection { c -> workOrders.setAttachmentSyncStatus(c, event.aggregateId, "SENT", result.externalId) }
                        }
                    } else {
                        println("1C sync FAILED: event=${event.id} type=${event.eventType} message=${result.message ?: "unknown"}")
                        markFailure(event, result.message ?: "1C rejected the request")
                    }
                }
                .onFailure { error ->
                    println("1C sync EXCEPTION: event=${event.id} type=${event.eventType} message=${error.message ?: error.javaClass.simpleName}")
                    markFailure(event, error.message ?: error.javaClass.simpleName)
                }
        }
    }

    private fun deliver(event: OutboxEvent): OneCResult {
        return when (event.eventType) {
            "OPERATION_STARTED" -> {
                val op = db.connection { c -> workOrders.findOperationById(c, event.aggregateId) }
                    ?: error("Operation ${event.aggregateId} not found")
                oneC.startOperation(op, event.id)
            }
            "OPERATION_COMPLETED" -> {
                val op = db.connection { c -> workOrders.findOperationById(c, event.aggregateId) }
                    ?: error("Operation ${event.aggregateId} not found")
                oneC.completeOperation(op, event.id)
            }
            "ISSUE_CREATED" -> {
                val issue = db.connection { c -> workOrders.findIssueById(c, event.aggregateId) }
                    ?: error("Issue ${event.aggregateId} not found")
                oneC.createIssue(issue, event.id)
            }
            "ATTACHMENT_UPLOADED" -> {
                val attachment = db.connection { c -> workOrders.findAttachmentById(c, event.aggregateId) }
                    ?: error("Attachment ${event.aggregateId} not found")
                val path = Path.of(config.storagePath.toString(), attachment.storedPath)
                oneC.uploadAttachment(attachment, path, event.id)
            }
            else -> error("Unknown outbox event type ${event.eventType}")
        }
    }

    private fun markFailure(event: OutboxEvent, message: String) {
        val retryAfter = minOf(300L, 5L * (1L shl minOf(event.attempts, 6)))
        sync.markFailed(event.id, message, retryAfter)
        if (event.eventType == "ATTACHMENT_UPLOADED") {
            db.connection { c -> workOrders.setAttachmentSyncStatus(c, event.aggregateId, "FAILED") }
        }
    }
}
