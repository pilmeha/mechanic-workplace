package com.example.mechanicworkplace.data.remote.onecmobile

import kotlinx.serialization.Serializable

/**
 * Every JSON response of the 1C service is wrapped into this envelope:
 *
 * `{ "result": {...}, "error": null }` on success,
 * `{ "result": null, "error": { "code", "message" } }` on failure.
 *
 * Binary photo responses (`GET /orders/{id}/photos/{photoId}`) are the only exception and
 * are parsed as raw bytes.
 */
@Serializable
data class ApiEnvelope<T>(
    val result: T? = null,
    val error: ApiError? = null
)
