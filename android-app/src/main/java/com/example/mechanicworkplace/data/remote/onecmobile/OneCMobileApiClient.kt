package com.example.mechanicworkplace.data.remote.onecmobile

import com.example.mechanicworkplace.data.auth.BasicAuthProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/**
 * Ktor implementation of [OneCMobileApi] for the 1C HTTP service.
 *
 * Design decisions:
 * - Basic header is injected by DefaultRequest for every call, including binary downloads;
 * - responses are parsed through [ApiEnvelope], except `getPhotoBinary`;
 * - 401 is handled by status only (1C platform may return an empty/non-JSON body);
 * - URLs are built with URLBuilder and relative paths without a leading `/`, so the
 *   `/test_mobile_1/hs/mobile/v1/` base path is never swallowed;
 * - only method/path/status are logged, never headers or bodies.
 *
 * There is no Idempotency-Key: the server ignores it. Callers must not blindly retry a
 * failed write; phase 6 reconciles by re-reading `GET /orders/{id}` first.
 * TODO(contract-clarification): acceptance of idempotency changes from "same key returns the
 * same result" to "unknown outcome → GET the card and decide". Confirm with the lead.
 */
class OneCMobileApiClient(
    baseUrl: String,
    private val authProvider: BasicAuthProvider,
    private val onUnauthorized: suspend () -> Unit = {},
    engine: HttpClientEngine = OkHttp.create(),
    private val json: Json = OneCMobileJson,
    private val logger: (String) -> Unit = {}
) : OneCMobileApi {

    private val baseUrl: String = normalizeBaseUrl(baseUrl)

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            authProvider.headerValue()?.let { header(HttpHeaders.Authorization, it) }
        }
    }

    override suspend fun login(): UserDto = safeCall {
        postEmpty<LoginResultDto>("auth/login").user
    }

    override suspend fun logout(): Unit = safeCall {
        postEmpty<OkResultDto>("auth/logout")
        Unit
    }

    override suspend fun getOrders(
        filter: OrderStateFilter,
        dateFrom: String?,
        dateTo: String?,
        search: String?,
        limit: Int,
        offset: Int
    ): PageDto<OrderListItemDto> = safeCall {
        getJson(
            path = "orders",
            query = listOf(
                "state" to filter.queryValue,
                "dateFrom" to dateFrom,
                "dateTo" to dateTo,
                "search" to search,
                "limit" to PageParams.normalizeLimit(limit).toString(),
                "offset" to PageParams.normalizeOffset(offset).toString()
            )
        )
    }

    override suspend fun getOrder(id: String): OrderCardDto = safeCall {
        getJson("orders/$id")
    }

    override suspend fun getStates(): List<StateCatalogItemDto> = safeCall {
        getJson<ItemsDto<StateCatalogItemDto>>("states").items
    }

    override suspend fun getDefects(search: String?): List<DefectCatalogItemDto> = safeCall {
        getJson<ItemsDto<DefectCatalogItemDto>>(
            path = "defects",
            query = listOf("search" to search?.takeIf { it.isNotBlank() })
        ).items
    }

    override suspend fun getPhotos(orderId: String): List<PhotoMetaDto> = safeCall {
        getJson<ItemsDto<PhotoMetaDto>>("orders/$orderId/photos").items
    }

    override suspend fun getPhotoBinary(orderId: String, photoId: String): ByteArray = safeCall {
        val path = "orders/$orderId/photos/$photoId"
        val response = client.get(buildUrl(path))
        log("GET", path, response)
        ensureAuthorized(response)
        if (response.status.value !in 200..299) {
            val text = response.bodyAsText()
            throw ApiErrorMapper.map(response.status.value, extractError(text))
        }
        response.bodyAsBytes()
    }

    override suspend fun deletePhoto(orderId: String, photoId: String): Unit = safeCall {
        deleteJson<OkResultDto>("orders/$orderId/photos/$photoId")
        Unit
    }

    override suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto =
        safeCall {
            sendJson(
                method = HttpMethod.Put,
                path = "orders/$orderId/works",
                body = request
            )
        }

    override suspend fun postOrder(orderId: String): PostResultDto = safeCall {
        postEmpty("orders/$orderId/post")
    }

    override suspend fun addDefect(orderId: String, request: AddDefectRequest): OrderCardDto =
        safeCall {
            sendJson(
                method = HttpMethod.Post,
                path = "orders/$orderId/defects",
                body = request
            )
        }

    override suspend fun uploadPhoto(orderId: String, request: UploadPhotoRequest): PhotoMetaDto =
        safeCall {
            sendJson<UploadPhotoRequest, UploadPhotoResultDto>(
                method = HttpMethod.Post,
                path = "orders/$orderId/photos",
                body = request
            ).photo
        }

    // ---------- internals ----------

    private suspend inline fun <reified T> getJson(
        path: String,
        query: List<Pair<String, String?>> = emptyList()
    ): T {
        val response = client.get(buildUrl(path, query))
        log("GET", path, response)
        return decodeEnvelope(response)
    }

    private suspend inline fun <reified T> postEmpty(path: String): T {
        val response = client.post(buildUrl(path))
        log("POST", path, response)
        return decodeEnvelope(response)
    }

    private suspend inline fun <reified B, reified T> sendJson(
        method: HttpMethod,
        path: String,
        body: B
    ): T {
        val response = client.request(buildUrl(path)) {
            this.method = method
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        log(method.value, path, response)
        return decodeEnvelope(response)
    }

    private suspend inline fun <reified T> deleteJson(path: String): T {
        val response = client.delete(buildUrl(path))
        log("DELETE", path, response)
        return decodeEnvelope(response)
    }

    private suspend fun ensureAuthorized(response: HttpResponse) {
        if (response.status.value != 401) return
        try {
            onUnauthorized()
        } catch (_: Throwable) {
            // Local cleanup must not hide the auth error from the caller.
        }
        throw OneCMobileException.Unauthorized()
    }

    private suspend inline fun <reified T> decodeEnvelope(response: HttpResponse): T {
        ensureAuthorized(response)
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw ApiErrorMapper.map(response.status.value, extractError(text))
        }
        val envelope = try {
            json.decodeFromString<ApiEnvelope<T>>(text)
        } catch (t: Throwable) {
            throw OneCMobileException.Unexpected(
                response.status.value,
                "Некорректный ответ 1С",
                t
            )
        }
        envelope.error?.let { throw ApiErrorMapper.map(response.status.value, it) }
        return envelope.result ?: throw OneCMobileException.Unexpected(
            response.status.value,
            "Пустой ответ 1С"
        )
    }

    private fun extractError(text: String?): ApiError? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<ApiEnvelope<JsonElement>>(text).error
        }.getOrNull() ?: runCatching {
            json.decodeFromString<ApiError>(text)
        }.getOrNull()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): T =
        try {
            block()
        } catch (t: OneCMobileException) {
            throw t
        } catch (t: HttpRequestTimeoutException) {
            throw OneCMobileException.Network("1С не ответила вовремя", t)
        } catch (t: IOException) {
            throw OneCMobileException.Network("Нет соединения с 1С", t)
        }

    private fun buildUrl(
        path: String,
        query: List<Pair<String, String?>> = emptyList()
    ): String {
        val builder = URLBuilder(baseUrl)
        builder.appendPathSegments(path.trim('/'))
        query.forEach { (name, value) ->
            if (!value.isNullOrBlank()) {
                builder.parameters.append(name, value)
            }
        }
        return builder.buildString()
    }

    private fun log(method: String, path: String, response: HttpResponse) {
        logger(LogSanitizer.redact("$method /${path.trimStart('/')} -> ${response.status.value}"))
    }

    private companion object {
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim()
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "API_BASE_URL must start with http:// or https://"
            }
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
