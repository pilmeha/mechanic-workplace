package com.example.mechanic.service

import io.ktor.http.HttpStatusCode

data class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: Map<String, String>? = null
) : RuntimeException(message)
