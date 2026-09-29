package com.example.mechanic.auth

import com.example.mechanic.model.UserDto
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.UUID

fun JWTPrincipal.user(): UserDto = UserDto(
    id = payload.subject,
    name = payload.getClaim("name").asString() ?: "",
    role = payload.getClaim("role").asString() ?: "MECHANIC",
    oneCEmployeeId = payload.getClaim("oneCEmployeeId").asString() ?: ""
)

fun JWTPrincipal.userId(): UUID = UUID.fromString(payload.subject)
