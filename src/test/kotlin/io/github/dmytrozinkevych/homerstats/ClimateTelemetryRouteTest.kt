package io.github.dmytrozinkevych.homerstats

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ClimateTelemetryRouteTest {

    @Test
    fun `POST climate-telemetry sends metrics and returns 204 NoContent on success`() = testApplication {
        // Given
        var capturedMetricsUrl = ""
        var capturedMetricsRequestBody = ""

        val mockHttpClient = HttpClient(
            MockEngine { request ->
                capturedMetricsUrl = request.url.toString()
                capturedMetricsRequestBody = request.body.toByteReadPacket().readText()
                respond("", HttpStatusCode.NoContent)
            }
        )
        val metricsSender = MetricsSender(
            "http://localhost:8428/api/v1/import",
            mockHttpClient,
            mainJsonSerializer
        )

        application {
            configureClimateTelemetryRoute(metricsSender, mainJsonSerializer)
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
        val mockHttpClient = HttpClient(
            MockEngine {
                respond("Bad Gateway", HttpStatusCode.BadGateway)
            }
        )
        val metricsSender = MetricsSender(
            "http://localhost:8428/api/v1/import",
            mockHttpClient,
            mainJsonSerializer
        )

        application {
            configureClimateTelemetryRoute(metricsSender, mainJsonSerializer)
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
        assertEquals("Failed to persist metrics", response.bodyAsText())
    }

    @Test
    fun `POST climate-telemetry returns 400 when receives malformed payload`() = testApplication {
        // Given
        application {
            configureClimateTelemetryRoute(dummyMetricsSender(), mainJsonSerializer)
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
                    "temperature": "cold",
                    "humidity": 49
                }
                """.trimIndent()
            )
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Invalid JSON payload", response.bodyAsText())
    }
}