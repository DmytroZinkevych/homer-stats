package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsSenderTest {

    @Test
    fun `formats metrics as NDJSON and sets stream-json content type`() = runTest {
        // Given
        var capturedBody = ""
        var capturedContentType: ContentType? = null

        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteReadPacket().readText()
            capturedContentType = request.body.contentType
            respond("", HttpStatusCode.NoContent)
        }

        val client = HttpClient(mockEngine)
        val sender = MetricsSender("http://localhost:8428/api/v1/import", client)

        val series = listOf(
            VmMetricSeries(
                mapOf(
                    "__name__" to "temperature_celsius",
                    "location" to "indoor",
                    "source" to "homepod"
                ),
                listOf(22.5f),
                listOf(1786988493000L)
            ),
            VmMetricSeries(
                mapOf(
                    "__name__" to "humidity_percents",
                    "location" to "indoor",
                    "source" to "homepod"
                ),
                listOf(49.0f),
                listOf(1786988493000L)
            )
        )

        // When
        val response = sender.sendMetrics(series)

        // Then
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(ContentType.parse("application/stream+json"), capturedContentType)

        val expectedNdjson = """
            {"metric":{"__name__":"temperature_celsius","location":"indoor","source":"homepod"},"values":[22.5],"timestamps":[1786988493000]}
            {"metric":{"__name__":"humidity_percents","location":"indoor","source":"homepod"},"values":[49.0],"timestamps":[1786988493000]}
            """.trimIndent()
        assertEquals(expectedNdjson, capturedBody)
    }
}
