package com.example.mechanicworkplace.data.remote

import com.example.mechanicworkplace.BuildConfig
import com.example.mechanicworkplace.model.*
import com.example.mechanicworkplace.data.session.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

class ApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

class ApiClient(private val sessionStore: SessionStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    private suspend inline fun <reified T> get(path: String): T {
        val token = sessionStore.token()
        val response = client.get(baseUrl + path) {
            token?.let { bearerAuth(it) }
        }
        return response.decode()
    }

    private suspend inline fun <reified B, reified T> postJson(
        path: String,
        body: B,
        idempotencyKey: String
    ): T {
        val token = sessionStore.token()
        val response = client.post(baseUrl + path) {
            token?.let { bearerAuth(it) }
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.decode()
    }

    suspend fun login(request: LoginRequest): TokenResponse {
        val response = client.post(baseUrl + "/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.decode()
    }

    suspend fun me(): UserDto = get("/api/v1/me")

    suspend fun workOrders(): WorkOrderListDto = get("/api/v1/work-orders")

    suspend fun workOrder(id: String): WorkOrderDetailsDto = get("/api/v1/work-orders/$id")

    suspend fun startOperation(workOrderId: String, operationId: String, key: String): StartOperationResponse =
        postJson("/api/v1/work-orders/$workOrderId/operations/$operationId/start", emptyMap<String, String>(), key)

    suspend fun completeOperation(
        workOrderId: String,
        operationId: String,
        request: CompleteOperationRequest,
        key: String
    ): CompleteOperationResponse =
        postJson("/api/v1/work-orders/$workOrderId/operations/$operationId/complete", request, key)

    suspend fun createIssue(
        workOrderId: String,
        request: CreateIssueRequest,
        key: String
    ): CreateIssueResponse =
        postJson("/api/v1/work-orders/$workOrderId/issues", request, key)

    suspend fun uploadAttachment(
        workOrderId: String,
        operationId: String?,
        file: File,
        fileName: String,
        mimeType: String,
        key: String
    ): AttachmentUploadResponse {
        val token = sessionStore.token()
        val response = client.post(baseUrl + "/api/v1/work-orders/$workOrderId/attachments") {
            token?.let { bearerAuth(it) }
            header("Idempotency-Key", key)
            operationId?.let { parameter("operationId", it) }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            file.readBytes(),
                            Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            }
                        )
                    }
                )
            )
        }
        return response.decode()
    }

    suspend fun syncStatus(): SyncStatusDto = get("/api/v1/sync/status")

    private suspend inline fun <reified T> io.ktor.client.statement.HttpResponse.decode(): T {
        if (status.value !in 200..299) {
            val text = runCatching { body<String>() }.getOrDefault(status.description)
            throw ApiException(status.value, text)
        }
        return body()
    }
}
