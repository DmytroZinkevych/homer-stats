package io.github.dmytrozinkevych.homerstats

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AppIntegrationTest {

    @Test
    fun `POST climate-telemetry sends metrics and returns 204 NoContent on success`() = testApplication {
        // Given
        var capturedMetricsUrl = ""
        var capturedMetricsRequestBody = ""

        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedMetricsUrl = request.url.toString()
                    capturedMetricsRequestBody = request.body.toByteReadPacket().readText()
                    respond("", HttpStatusCode.NoContent)
                }
            }
        }
        val metricsSender = MetricsSender("http://localhost:8428/api/v1/import", mockHttpClient)

        application {
            install(ContentNegotiation) {
                json()
            }
            configureClimateTelemetryRoute(metricsSender)
        }

        // When
        val response = client.post("/api/climate-telemetry") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "timestamp": "2026-08-17T19:41:33+02:00",
                    "location": "indoor",
                    "source": "homepod",
                    "temperature": 22.5,
                    "humidity": 49
                }
                """.trimIndent()
            )
        }

        // Then
        val expectedNdjson = """
            {"metric":{"__name__":"temperature_celsius","location":"indoor","source":"homepod"},"values":[22.5],"timestamps":[1786988493000]}
            {"metric":{"__name__":"humidity_percents","location":"indoor","source":"homepod"},"values":[49.0],"timestamps":[1786988493000]}
            """.trimIndent()

        assertEquals("http://localhost:8428/api/v1/import", capturedMetricsUrl)
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(expectedNdjson, capturedMetricsRequestBody)
    }

    @Test
    fun `POST climate-telemetry returns 500 when sending metrics fail`() = testApplication {
        // Given
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("Internal Server Error", HttpStatusCode.InternalServerError)
                }
            }
        }
        val metricsSender = MetricsSender("http://localhost:8428/api/v1/import", mockHttpClient)

        application {
            install(ContentNegotiation) {
                json()
            }
            configureClimateTelemetryRoute(metricsSender)
        }

        // When
        val response = client.post("/api/climate-telemetry") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "timestamp": "2026-08-17T19:41:33+02:00",
                    "location": "indoor",
                    "source": "homepod",
                    "temperature": 22.5,
                    "humidity": 49
                }
                """.trimIndent()
            )
        }

        // Then
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}