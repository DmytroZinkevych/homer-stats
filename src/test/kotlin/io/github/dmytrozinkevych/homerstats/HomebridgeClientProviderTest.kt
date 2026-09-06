package io.github.dmytrozinkevych.homerstats

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private const val HOMEBRIDGE_URL = "http://127.0.0.1:8581"
private const val TEST_USER = "user"
private const val TEST_PASSWORD = "password"

class HomebridgeClientProviderTest {

    @Test
    fun `should authenticate and attach bearer token on request`() = runBlocking {
        var capturedCredentials = ""
        var capturedToken: String? = null

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/auth/login" -> {
                    capturedCredentials = request.body.toByteReadPacket().readText()
                    respond(
                        content = """{"access_token": "test-token"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                "/api/accessories" -> {
                    capturedToken = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = """{"field": "value"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> error("Unhandled request: ${request.url.encodedPath}")
            }
        }

        HomebridgeClientProvider(
            homebridgeUrl = HOMEBRIDGE_URL,
            user = TEST_USER,
            password = TEST_PASSWORD,
            jsonSerializer = mainJsonSerializer,
            httpClientEngineFactory = mockEngine.asFactory()
        ).createClient().use { client ->

            val response = client.get("$HOMEBRIDGE_URL/api/accessories")

            assertEquals("""{"username":"$TEST_USER","password":"$TEST_PASSWORD"}""", capturedCredentials)
            assertEquals("Bearer test-token", capturedToken)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"field": "value"}""", response.bodyAsText())
        }
    }

    @Test
    fun `should execute refreshTokens on 401 Unauthorized response and cache it`() = runBlocking {
        var tokenFetchCount = 0
        var refreshedToken: String? = null

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/auth/login" -> {
                    tokenFetchCount++
                    val tokenValue = if (tokenFetchCount == 1) {
                        "expired-token"
                    } else {
                        "refreshed-token"
                    }
                    respond(
                        content = """{"access_token": "$tokenValue"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                "/api/accessories" -> {
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer expired-token") {
                        respond(content = "Unauthorized", status = HttpStatusCode.Unauthorized)
                    } else {
                        refreshedToken = authHeader
                        respond(
                            content = """{"status": "ok"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
                else -> error("Unhandled request: ${request.url.encodedPath}")
            }
        }

        HomebridgeClientProvider(
            homebridgeUrl = HOMEBRIDGE_URL,
            user = TEST_USER,
            password = TEST_PASSWORD,
            jsonSerializer = mainJsonSerializer,
            httpClientEngineFactory = mockEngine.asFactory()
        ).createClient().use { client ->

            val response = client.get("$HOMEBRIDGE_URL/api/accessories")

            assertEquals(2, tokenFetchCount, "Token should have been fetched twice (initial load + refresh)")
            assertEquals("Bearer refreshed-token", refreshedToken)
            assertEquals(HttpStatusCode.OK, response.status)

            val response2 = client.get("$HOMEBRIDGE_URL/api/accessories")
            assertEquals(2, tokenFetchCount, "Should not re-fetch cached token")
            assertEquals(HttpStatusCode.OK, response2.status)
        }
    }
}
