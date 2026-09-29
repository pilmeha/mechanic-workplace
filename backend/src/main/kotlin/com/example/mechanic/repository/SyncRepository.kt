package com.example.mechanic.repository

import com.example.mechanic.db.Database
import com.example.mechanic.model.OutboxEvent
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class IdempotencyRecord(
    val id: UUID,
    val userId: UUID,
    val key: String,
    val route: String,
    val status: String,
    val responseStatus: Int?,
    val responseBody: String?
)

class SyncRepository(private val db: Database) {
    fun tryStartIdempotency(c: Connection, userId: UUID, key: String, route: String): Boolean =
        c.prepareStatement("""
            INSERT INTO idempotency_records(id, user_id, idem_key, route, status)
            VALUES (?, ?, ?, ?, 'PROCESSING')
            ON CONFLICT (user_id, idem_key) DO NOTHING
        """.trimIndent()).use { ps ->
            ps.setObject(1, UUID.randomUUID())
            ps.setObject(2, userId)
            ps.setString(3, key)
            ps.setString(4, route)
            ps.executeUpdate() == 1
        }

    fun getIdempotency(c: Connection, userId: UUID, key: String): IdempotencyRecord? =
        c.prepareStatement("""
            SELECT id, user_id, idem_key, route, status, response_status, response_body
            FROM idempotency_records WHERE user_id = ? AND idem_key = ?
        """.trimIndent()).use { ps ->
            ps.setObject(1, userId)
            ps.setString(2, key)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else IdempotencyRecord(
                    id = rs.getObject("id", UUID::class.java),
                    userId = rs.getObject("user_id", UUID::class.java),
                    key = rs.getString("idem_key"),
                    route = rs.getString("route"),
                    status = rs.getString("status"),
                    responseStatus = rs.getObject("response_status")?.let { (it as Number).toInt() },
                    responseBody = rs.getString("response_body")
                )
            }
        }

    fun completeIdempotency(c: Connection, userId: UUID, key: String, responseStatus: Int, responseBody: String) {
        c.prepareStatement("""
            UPDATE idempotency_records
            SET status = 'COMPLETED', response_status = ?, response_body = ?, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND idem_key = ?
        """.trimIndent()).use { ps ->
            ps.setInt(1, responseStatus)
            ps.setString(2, responseBody)
            ps.setObject(3, userId)
            ps.setString(4, key)
            ps.executeUpdate()
        }
    }

    fun insertOutbox(c: Connection, eventType: String, aggregateId: UUID, payload: String): UUID {
        val id = UUID.randomUUID()
        c.prepareStatement("""
            INSERT INTO outbox_events(id, event_type, aggregate_id, payload)
            VALUES (?, ?, ?, ?)
        """.trimIndent()).use { ps ->
            ps.setObject(1, id)
            ps.setString(2, eventType)
            ps.setObject(3, aggregateId)
            ps.setString(4, payload)
            ps.executeUpdate()
        }
        return id
    }

    fun resetStaleProcessing() = db.connection { c ->
        c.prepareStatement("""
            UPDATE outbox_events
            SET status = 'PENDING', locked_at = NULL
            WHERE status = 'PROCESSING' AND locked_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
        """.trimIndent()).use { it.executeUpdate() }
    }

    fun claimBatch(limit: Int = 20): List<OutboxEvent> = db.transaction { c ->
        val events = mutableListOf<OutboxEvent>()
        c.prepareStatement("""
            SELECT id, event_type, aggregate_id, payload, status, attempts, available_at, locked_at
            FROM outbox_events
            WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT ?
        """.trimIndent()).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    events += OutboxEvent(
                        id = rs.getObject("id", UUID::class.java),
                        eventType = rs.getString("event_type"),
                        aggregateId = rs.getObject("aggregate_id", UUID::class.java),
                        payload = rs.getString("payload"),
                        status = rs.getString("status"),
                        attempts = rs.getInt("attempts"),
                        availableAt = rs.getTimestamp("available_at").toInstant(),
                        lockedAt = rs.getTimestamp("locked_at")?.toInstant()
                    )
                }
            }
        }
        if (events.isNotEmpty()) {
            c.prepareStatement("""
                UPDATE outbox_events
                SET status = 'PROCESSING', locked_at = CURRENT_TIMESTAMP, attempts = attempts + 1
                WHERE id = ANY(?)
            """.trimIndent()).use { ps ->
                val array = c.createArrayOf("uuid", events.map { it.id }.toTypedArray())
                ps.setArray(1, array)
                ps.executeUpdate()
            }
        }
        events.map { it.copy(status = "PROCESSING", attempts = it.attempts + 1, lockedAt = Instant.now()) }
    }

    fun markSent(eventId: UUID) = db.connection { c ->
        c.prepareStatement("UPDATE outbox_events SET status = 'SENT', locked_at = NULL, last_error = NULL WHERE id = ?").use { ps ->
            ps.setObject(1, eventId)
            ps.executeUpdate()
        }
    }

    fun markFailed(eventId: UUID, error: String, retryAfterSeconds: Long) = db.connection { c ->
        c.prepareStatement("""
            UPDATE outbox_events
            SET status = 'PENDING', locked_at = NULL, last_error = ?,
                available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
            WHERE id = ?
        """.trimIndent()).use { ps ->
            ps.setString(1, error.take(2000))
            ps.setLong(2, retryAfterSeconds)
            ps.setObject(3, eventId)
            ps.executeUpdate()
        }
    }

    fun statusCounts(): Map<String, Int> = db.connection { c ->
        c.prepareStatement("SELECT status, COUNT(*) FROM outbox_events GROUP BY status").use { ps ->
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) put(rs.getString(1), rs.getLong(2).toInt())
                }
            }
        }
    }
}
