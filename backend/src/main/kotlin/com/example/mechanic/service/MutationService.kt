package com.example.mechanic.service

import com.example.mechanic.db.Database
import com.example.mechanic.repository.IdempotencyRecord
import com.example.mechanic.repository.SyncRepository
import java.util.UUID

data class StoredResponse(val status: Int, val body: String)

class MutationService(
    private val db: Database,
    private val syncRepository: SyncRepository
) {
    fun execute(
        userId: UUID,
        idempotencyKey: String?,
        route: String,
        statusCode: Int = 200,
        block: (java.sql.Connection) -> String
    ): StoredResponse {
        val key = idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException(io.ktor.http.HttpStatusCode.BadRequest, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required")
        if (key.length > 200) {
            throw ApiException(io.ktor.http.HttpStatusCode.BadRequest, "IDEMPOTENCY_KEY_TOO_LONG", "Idempotency-Key must be at most 200 characters")
        }

        return db.transaction { c ->
            val inserted = syncRepository.tryStartIdempotency(c, userId, key, route)
            if (!inserted) {
                val existing = syncRepository.getIdempotency(c, userId, key)
                    ?: throw IllegalStateException("Idempotency record disappeared")
                when (existing.status) {
                    "COMPLETED" -> StoredResponse(existing.responseStatus ?: 200, existing.responseBody ?: "{}")
                    "PROCESSING" -> throw ApiException(io.ktor.http.HttpStatusCode.Conflict, "IDEMPOTENCY_IN_PROGRESS", "The same request is already being processed")
                    else -> throw ApiException(io.ktor.http.HttpStatusCode.Conflict, "IDEMPOTENCY_RETRY_STATE", "The idempotency record is not in a retryable state")
                }
            } else {
                val body = block(c)
                syncRepository.completeIdempotency(c, userId, key, statusCode, body)
                StoredResponse(statusCode, body)
            }
        }
    }
}
