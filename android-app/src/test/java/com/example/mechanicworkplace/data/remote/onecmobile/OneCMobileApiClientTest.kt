package com.example.mechanicworkplace.data.remote.onecmobile

import com.example.mechanicworkplace.data.auth.BasicAuthProvider
import com.example.mechanicworkplace.data.auth.Credentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OneCMobileApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val baseUrl = "http://178.47.32.166/test_mobile_1/hs/mobile/v1"

    @Test
    fun `login sends Basic header and no content type`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = """
                    {"result":{"user":{"userId":"u-1","name":"Иванов Иван","employeeId":"e-1","isMaster":true}},"error":null}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val user = client(engine).login()

        assertEquals("u-1", user.userId)
        assertEquals("e-1", user.employeeId)
        assertTrue(user.isMaster)

        val request = requireNotNull(captured)
        assertEquals("POST", request.method.value)
        assertEquals("Basic dXNlcjpwYXNz", request.headers[HttpHeaders.Authorization])
        assertNull(request.headers[HttpHeaders.ContentType])
        assertTrue(request.url.toString().endsWith("/hs/mobile/v1/auth/login"))
    }

    @Test
    fun `orders uses base path and pagination defaults and caps`() = runTest {
        val lastUrl = AtomicReference<String>()
        val engine = MockEngine { request ->
            lastUrl.set(request.url.toString())
            respond(
                content = """{"result":{"items":[],"total":0,"limit":50,"offset":0},"error":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val api = client(engine)

        api.getOrders()
        assertTrue(lastUrl.get().startsWith("$baseUrl/orders?"))
        assertTrue(lastUrl.get().contains("limit=50"))
        assertTrue(lastUrl.get().contains("offset=0"))

        api.getOrders(limit = 500, offset = -10)
        assertTrue(lastUrl.get().contains("limit=200"))
        assertTrue(lastUrl.get().contains("offset=0"))

        api.getOrders(filter = OrderStateFilter.CURRENT)
        assertTrue(lastUrl.get().contains("state=current"))
    }

    @Test
    fun `401 is handled without parsing body and notifies session`() = runTest {
        val unauthorizedCalls = AtomicInteger()
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        val api = client(engine, onUnauthorized = { unauthorizedCalls.incrementAndGet() })

        val error = runCatching { api.getOrder("order-1") }.exceptionOrNull()

        assertTrue(error is OneCMobileException.Unauthorized)
        assertEquals(1, unauthorizedCalls.get())
    }

    @Test
    fun `409 keeps original server message`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {"result":null,"error":{"code":"CONFLICT","message":"Заказ-наряд уже проведён"}}
                """.trimIndent(),
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders
            )
        }

        val error = runCatching { client(engine).postOrder("order-1") }.exceptionOrNull()

        assertTrue(error is OneCMobileException.Conflict)
        assertEquals("Заказ-наряд уже проведён", error?.message)
    }

    @Test
    fun `413 and 415 map to file errors`() {
        val tooLarge = ApiErrorMapper.map(413, null)
        val unsupported = ApiErrorMapper.map(415, null)
        assertTrue(tooLarge is OneCMobileException.TooLarge)
        assertTrue(unsupported is OneCMobileException.UnsupportedMediaType)
    }

    @Test
    fun `photo binary is read as bytes and carries Basic header`() = runTest {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "image/jpeg")
            )
        }

        val result = client(engine).getPhotoBinary("order-1", "photo-1")

        assertArrayEquals(bytes, result)
        val request = requireNotNull(captured)
        assertEquals("GET", request.method.value)
        assertEquals("Basic dXNlcjpwYXNz", request.headers[HttpHeaders.Authorization])
        assertTrue(request.url.toString().endsWith("/orders/order-1/photos/photo-1"))
    }

    @Test
    fun `upload photo sends JSON base64 instead of multipart`() = runTest {
        val bodyText = AtomicReference<String>()
        val engine = MockEngine { request ->
            bodyText.set((request.body as TextContent).text)
            respond(
                content = """
                    {"result":{"photo":{"id":"photo-1","fileName":"photo_1.jpg","size":4,"uploadedBy":"Иван","uploadedAt":"2026-11-12T10:12:00","canDelete":true}},"error":null}
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = jsonHeaders
            )
        }

        val photo = client(engine).uploadPhoto(
            orderId = "order-1",
            request = UploadPhotoRequest(
                fileName = "photo_1.jpg",
                contentType = "image/jpeg",
                contentBase64 = "/9j/AA=="
            )
        )

        assertEquals("photo-1", photo.id)
        assertTrue(photo.canDelete)
        val body = bodyText.get()
        assertTrue(body.contains("\"fileName\":\"photo_1.jpg\""))
        assertTrue(body.contains("\"contentType\":\"image/jpeg\""))
        assertTrue(body.contains("\"contentBase64\":\"/9j/AA==\""))
        assertFalse(body.contains("multipart"))
    }

    @Test
    fun `logout tolerates empty body`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":{"ok":true},"error":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        client(engine).logout()
    }

    private fun client(
        engine: MockEngine,
        onUnauthorized: suspend () -> Unit = {}
    ): OneCMobileApiClient {
        val provider = BasicAuthProvider().apply {
            set(Credentials(login = "user", password = "pass"))
        }
        return OneCMobileApiClient(
            baseUrl = baseUrl,
            authProvider = provider,
            onUnauthorized = onUnauthorized,
            engine = engine
        )
    }
}
