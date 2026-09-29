package com.example.mechanic

import com.example.mechanic.auth.JwtService
import com.example.mechanic.auth.PasswordHasher
import com.example.mechanic.config.AppConfig
import com.example.mechanic.db.Database
import com.example.mechanic.onec.createOneCClient
import com.example.mechanic.outbox.OutboxWorker
import com.example.mechanic.repository.SyncRepository
import com.example.mechanic.repository.UserRepository
import com.example.mechanic.repository.WorkOrderRepository
import com.example.mechanic.service.ApiException
import com.example.mechanic.service.FileStorage
import com.example.mechanic.service.MechanicService
import com.example.mechanic.service.MutationService
import com.example.mechanic.web.apiRoutes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.netty.EngineMain
import io.ktor.server.netty.Netty
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.plugins.swagger.swaggerUI
import kotlinx.serialization.json.Json
import java.util.UUID

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, port = config.serverPort, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val config = AppConfig.fromEnvironment()
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val db = Database(config)
    val passwordHasher = PasswordHasher()
    val jwtService = JwtService(config)
    val users = UserRepository(db)
    val workOrders = WorkOrderRepository(db)
    val sync = SyncRepository(db)
    val mutations = MutationService(db, sync)
    val service = MechanicService(db, users, workOrders, sync, mutations, passwordHasher, jwtService, json)
    val storage = FileStorage(config.storagePath)
    val oneC = createOneCClient(config)
    val outboxWorker = OutboxWorker(config, db, sync, workOrders, oneC)

    environment.monitor.subscribe(ApplicationStarted) { outboxWorker.start() }
    environment.monitor.subscribe(ApplicationStopped) {
        outboxWorker.stop()
        db.close()
    }

    install(CallLogging)
    install(ContentNegotiation) { json(json) }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            val requestId = call.request.header("X-Request-Id")
            call.response.header("X-Request-Id", requestId ?: UUID.randomUUID().toString())
            call.respond(
                cause.status,
                com.example.mechanic.model.ApiError(cause.code, cause.message, requestId, cause.details)
            )
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            val requestId = call.request.header("X-Request-Id") ?: UUID.randomUUID().toString()
            call.response.header("X-Request-Id", requestId)
            call.respond(
                HttpStatusCode.InternalServerError,
                com.example.mechanic.model.ApiError("INTERNAL_ERROR", "Internal server error", requestId)
            )
        }
    }

    install(io.ktor.server.auth.Authentication) {
        jwt("auth-jwt") {
            verifier(jwtService.verifier)
            validate { credential ->
                if (credential.payload.subject?.let { runCatching { UUID.fromString(it) }.isSuccess } == true) {
                    io.ktor.server.auth.jwt.JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    routing {
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        get("/ready") {
            // Database initialization happens before routing becomes available.
            call.respondText("READY", ContentType.Text.Plain)
        }
        swaggerUI(path = "docs", swaggerFile = "openapi/openapi.yaml")
        apiRoutes(service, storage, json)
    }
}

