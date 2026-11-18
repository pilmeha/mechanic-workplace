package com.example.mechanicworkplace.data.remote.onecmobile

import kotlinx.serialization.json.Json

/**
 * Single JSON configuration for the 1C mobile API.
 *
 * `ignoreUnknownKeys` is mandatory: the server may add fields, and some sample responses
 * contain `worksCount`/`hasPhotos` even though the contract says they are not guaranteed.
 */
val OneCMobileJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

/** Pagination limits from the contract: default 50, maximum 200. */
object PageParams {
    const val DEFAULT_LIMIT = 50
    const val MAX_LIMIT = 200

    fun normalizeLimit(value: Int): Int =
        if (value <= 0) DEFAULT_LIMIT else value.coerceAtMost(MAX_LIMIT)

    fun normalizeOffset(value: Int): Int =
        if (value < 0) 0 else value
}
