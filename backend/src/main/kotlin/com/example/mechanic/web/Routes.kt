package com.example.mechanic.web

import com.example.mechanic.auth.userId
import com.example.mechanic.model.*
import com.example.mechanic.service.ApiException
import com.example.mechanic.service.FileStorage
import com.example.mechanic.service.MechanicService
import com.example.mechanic.service.StoredResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.UUID
import io.ktor.http.content.*

fun Route.apiRoutes(service: MechanicService, storage: FileStorage, json: Json) {
    route("/api/v1") {
        post("/auth/login") {
            val request = call.receive<LoginRequest>()
            call.respond(service.login(request))
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>() ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Authentication required")
                call.respond(
                    UserDto(
                        id = principal.payload.subject,
                        name = principal.payload.getClaim("name").asString() ?: "",
                        role = principal.payload.getClaim("role").asString() ?: "MECHANIC",
                        oneCEmployeeId = principal.payload.getClaim("oneCEmployeeId").asString() ?: ""
                    )
                )
            }

            get("/work-orders") {
                call.respond(service.listWorkOrders(call.principal<JWTPrincipal>()!!.userId()))
            }

            get("/work-orders/{id}") {
                val id = call.pathUuid("id")
                call.respond(service.getWorkOrder(call.principal<JWTPrincipal>()!!.userId(), id))
            }

            post("/work-orders/{workOrderId}/operations/{operationId}/start") {
                val workOrderId = call.pathUuid("workOrderId")
                val operationId = call.pathUuid("operationId")
                val result = service.startOperation(
                    call.principal<JWTPrincipal>()!!.userId(),
                    workOrderId,
                    operationId,
                    call.request.header("Idempotency-Key")
                )
                call.respondStored(result)
            }

            post("/work-orders/{workOrderId}/operations/{operationId}/complete") {
                val workOrderId = call.pathUuid("workOrderId")
                val operationId = call.pathUuid("operationId")
                val request = call.receive<CompleteOperationRequest>()
                val result = service.completeOperation(
                    call.principal<JWTPrincipal>()!!.userId(),
                    workOrderId,
                    operationId,
                    request,
                    call.request.header("Idempotency-Key")
                )
                call.respondStored(result)
            }

            post("/work-orders/{workOrderId}/issues") {
                val workOrderId = call.pathUuid("workOrderId")
                val request = call.receive<CreateIssueRequest>()
                val result = service.createIssue(
                    call.principal<JWTPrincipal>()!!.userId(),
                    workOrderId,
                    request,
                    call.request.header("Idempotency-Key")
                )
                call.respondStored(result)
            }

            post("/work-orders/{workOrderId}/attachments") {
                val workOrderId = call.pathUuid("workOrderId")
                val operationId = call.request.queryParameters["operationId"]?.let { raw: String -> parseUuidQuery(raw, "operationId") }
                val idempotencyKey = call.request.header("Idempotency-Key")
                    ?: throw ApiException(HttpStatusCode.BadRequest, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required")
                val multipart = call.receiveMultipart(formFieldLimit = 12L * 1024 * 1024)
                var saved: com.example.mechanic.service.SavedFile? = null
                var contentType = ContentType.Image.JPEG.toString()
                multipart.forEachPart { part: PartData ->
                    try {
                        if (part is PartData.FileItem && saved == null) {
                            contentType = part.contentType?.toString() ?: ContentType.Image.JPEG.toString()
                            saved = storage.save(part, workOrderId, 10L * 1024 * 1024)
                        }
                    } finally {
                        part.dispose()
                    }
                }
                val file = saved ?: throw ApiException(HttpStatusCode.BadRequest, "FILE_REQUIRED", "multipart field 'file' is required")
                val result = service.createAttachment(
                    call.principal<JWTPrincipal>()!!.userId(),
                    workOrderId,
                    operationId,
                    file,
                    contentType,
                    idempotencyKey
                )
                call.respondStored(result)
            }

            get("/attachments/{id}/file") {
                val id = call.pathUuid("id")
                val attachment = service.getAttachment(call.principal<JWTPrincipal>()!!.userId(), id)
                val file = storage.resolve(attachment.storedPath)
                if (!Files.exists(file)) throw ApiException(HttpStatusCode.NotFound, "FILE_NOT_FOUND", "Attachment file not found")
                call.response.header(HttpHeaders.ContentType, attachment.contentType)
                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${attachment.originalName.replace("\"", "_")}\"")
                call.respondFile(file.toFile())
            }

            get("/sync/status") {
                call.respond(service.syncStatus())
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondStored(result: StoredResponse) {
    response.status(HttpStatusCode.fromValue(result.status))
    respondText(result.body, ContentType.Application.Json)
}

private fun io.ktor.server.application.ApplicationCall.pathUuid(name: String): UUID {
    val raw = parameters[name] ?: throw ApiException(HttpStatusCode.BadRequest, "INVALID_PATH", "Path parameter '$name' is required")
    return runCatching { UUID.fromString(raw) }
        .getOrElse { throw ApiException(HttpStatusCode.BadRequest, "INVALID_UUID", "Path parameter '$name' must be a UUID") }
}

private fun parseUuidQuery(raw: String, name: String): UUID =
    runCatching { UUID.fromString(raw) }
        .getOrElse { throw ApiException(HttpStatusCode.BadRequest, "INVALID_UUID", "$name must be a UUID") }
