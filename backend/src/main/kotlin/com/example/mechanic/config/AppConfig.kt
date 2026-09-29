package com.example.mechanic.config

import java.nio.file.Path
import java.nio.file.Paths

data class AppConfig(
    val serverPort: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtTtlSeconds: Long,
    val storagePath: Path,
    val oneCMode: OneCMode,
    val oneCBaseUrl: String,
    val oneCUsername: String?,
    val oneCPassword: String?,
    val syncIntervalMs: Long
) {
    enum class OneCMode { DEMO, HTTP }

    companion object {
        fun fromEnvironment(): AppConfig {
            fun env(name: String, default: String): String =
                System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

            fun envLong(name: String, default: Long): Long =
                env(name, default.toString()).toLong()

            return AppConfig(
                serverPort = env("SERVER_PORT", "8080").toInt(),
                databaseUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/mechanic"),
                databaseUser = env("DATABASE_USER", "mechanic"),
                databasePassword = env("DATABASE_PASSWORD", "mechanic"),
                jwtSecret = env("JWT_SECRET", "dev-only-change-me-to-a-long-random-secret"),
                jwtIssuer = env("JWT_ISSUER", "mechanic-backend"),
                jwtAudience = env("JWT_AUDIENCE", "mechanic-android"),
                jwtTtlSeconds = envLong("JWT_TTL_SECONDS", 8 * 60 * 60),
                storagePath = Paths.get(env("STORAGE_PATH", "./storage")),
                oneCMode = env("ONEC_MODE", "DEMO").uppercase().let {
                    runCatching { OneCMode.valueOf(it) }.getOrDefault(OneCMode.DEMO)
                },
                oneCBaseUrl = env("ONEC_BASE_URL", "http://localhost:8081/hs/mechanic/v1"),
                oneCUsername = System.getenv("ONEC_USERNAME"),
                oneCPassword = System.getenv("ONEC_PASSWORD"),
                syncIntervalMs = envLong("SYNC_INTERVAL_MS", 5000)
            )
        }
    }
}
