package com.example.mechanic.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.mechanic.config.AppConfig
import com.example.mechanic.model.UserDto
import java.time.Instant
import java.util.Date

class JwtService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val ttlSeconds: Long get() = config.jwtTtlSeconds

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .build()

    fun createToken(user: UserDto): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(user.id)
            .withClaim("name", user.name)
            .withClaim("role", user.role)
            .withClaim("oneCEmployeeId", user.oneCEmployeeId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(config.jwtTtlSeconds)))
            .sign(algorithm)
    }
}
