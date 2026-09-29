package com.example.mechanic.onec

import com.example.mechanic.model.AttachmentRecord
import com.example.mechanic.model.IssueRecord
import com.example.mechanic.model.OperationRecord
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

interface OneCClient {
    fun startOperation(operation: OperationRecord, idempotencyKey: java.util.UUID): OneCResult
    fun completeOperation(operation: OperationRecord, idempotencyKey: java.util.UUID): OneCResult
    fun createIssue(issue: IssueRecord, idempotencyKey: java.util.UUID): OneCResult
    fun uploadAttachment(attachment: AttachmentRecord, filePath: Path, idempotencyKey: java.util.UUID): OneCResult
}

data class OneCResult(
    val success: Boolean,
    val externalId: String? = null,
    val message: String? = null
)

class DemoOneCClient : OneCClient {
    override fun startOperation(operation: OperationRecord, idempotencyKey: java.util.UUID): OneCResult =
        OneCResult(true, operation.oneCId ?: "DEMO-${operation.id}", "Demo 1C: operation started")

    override fun completeOperation(operation: OperationRecord, idempotencyKey: java.util.UUID): OneCResult =
        OneCResult(true, operation.oneCId ?: "DEMO-${operation.id}", "Demo 1C: operation completed")

    override fun createIssue(issue: IssueRecord, idempotencyKey: java.util.UUID): OneCResult =
        OneCResult(true, issue.oneCId ?: "DEMO-${issue.id}", "Demo 1C: issue created")

    override fun uploadAttachment(attachment: AttachmentRecord, filePath: Path, idempotencyKey: java.util.UUID): OneCResult =
        OneCResult(true, attachment.oneCId ?: "DEMO-${attachment.id}", "Demo 1C: attachment uploaded")
}

/**
 * Generic adapter for a future 1C HTTP service.
 *
 * The service is deliberately not coupled to Alfa-Auto metadata. When the real 1C
 * endpoint becomes known, only URL mapping / authentication / payload mapping should change.
 */
class HttpOneCClient(
    private val baseUrl: String,
    private val username: String?,
    private val password: String?
) : OneCClient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun startOperation(operation: OperationRecord, idempotencyKey: java.util.UUID): OneCResult =
        postJson(
            "/operations/${operation.oneCId ?: operation.id}/start",
            """{"operationId":"${operation.oneCId ?: operation.id}","status":"IN_PROGRESS","startedAt":"${operation.startedAt}"}""",
            idempotencyKey
        )

    override fun completeOperation(operation: OperationRecord, idempotencyKey: java.util.UUID): OneCResult =
        postJson(
            "/operations/${operation.oneCId ?: operation.id}/complete",
            """{"operationId":"${operation.oneCId ?: operation.id}","status":"COMPLETED","completedAt":"${operation.completedAt}","description":${jsonString(operation.description)}}""",
            idempotencyKey
        )

    override fun createIssue(issue: IssueRecord, idempotencyKey: java.util.UUID): OneCResult =
        postJson(
            "/issues",
            """{"issueId":"${issue.id}","workOrderId":"${issue.workOrderId}","operationId":${jsonString(issue.operationId?.toString())},"description":${jsonString(issue.description)},"additionalWorkRequired":${issue.additionalWorkRequired}}""",
            idempotencyKey
        )

    override fun uploadAttachment(attachment: AttachmentRecord, filePath: Path, idempotencyKey: java.util.UUID): OneCResult {
        val bytes = Files.readAllBytes(filePath)
        return postJson(
            "/files",
            """{"attachmentId":"${attachment.id}","workOrderId":"${attachment.workOrderId}","operationId":${jsonString(attachment.operationId?.toString())},"fileName":${jsonString(attachment.originalName)},"contentType":${jsonString(attachment.contentType)},"contentBase64":${jsonString(Base64.getEncoder().encodeToString(bytes))}}""",
            idempotencyKey
        )
    }

    private fun postJson(path: String, body: String, idempotencyKey: java.util.UUID): OneCResult {
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey.toString())
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (!username.isNullOrBlank() && password != null) {
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray()))
        }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val success = response.statusCode() in 200..299
        return OneCResult(
            success = success,
            externalId = if (success) response.headers().firstValue("X-1C-Object-Id").orElse(null) else null,
            message = if (success) response.body().ifBlank { null } else "1C returned HTTP ${response.statusCode()}: ${response.body()}"
        )
    }

    private fun jsonString(value: String?): String =
        value?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
}
