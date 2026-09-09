package io.github.dmytrozinkevych.homerstats

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val HOMEBRIDGE_URL = "http://localhost:8581"
private const val VM_IMPORT_URL = "http://localhost:8428/api/v1/import"

private val interval = 5.seconds
private val offset = 500.milliseconds

class AirQualityPollerTest {
    @Test
    fun `fetches PM 2_5 metric from active device and pushes to VictoriaMetrics`() = runTest(timeout = 3.seconds) {
        // Given
        var capturedHomebridgeUrl = ""
        val receivedMetricChannel = Channel<String>(capacity = 1)

        val mockHomebridgeEngine = MockEngine { request ->
            capturedHomebridgeUrl = request.url.toString()
            respond(
                content = """
                [
                    {
                        "accessoryInformation": {
                            "Name": "@@@ My-AirPurifier @@@"
                        },
                        "values": {
                            "StatusActive": 1,
                            "PM2_5Density": 7
                        }
                    }
                ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val mockMetricsSenderEngine = MockEngine { request ->
            receivedMetricChannel.trySend(request.body.toByteReadPacket().readText())
            respond("", HttpStatusCode.NoContent)
        }

        val metricsSender = MetricsSender(VM_IMPORT_URL, HttpClient(mockMetricsSenderEngine), mainJsonSerializer)
        val poller = AirQualityPoller(
            interval = interval,
            offset = offset,
            homebridgeUrl = HOMEBRIDGE_URL,
            homebridgeClient = HttpClient(mockHomebridgeEngine),
            metricsSender = metricsSender,
            jsonSerializer = mainJsonSerializer
        )

        // When
        val job = launch { poller.startPolling(this) }

        val capturedMetric = receivedMetricChannel.receive()

        // Then
        val expectedMetricSnippet = """"__name__":"pm_2_5_density","location":"indoor","source":"my_air_purifier""""
        val expectedValueSnippet = """"values":[7.0]"""

        assertEquals("http://localhost:8581/api/accessories", capturedHomebridgeUrl)

        assertContains(capturedMetric, expectedMetricSnippet)
        assertContains(capturedMetric, expectedValueSnippet)

        job.cancelAndJoin()
    }

    @Test
    fun `fetchAirQualityData() returns null when device StatusActive is 0 (powered off)`() = runTest {
        // Given
        val mockHomebridgeEngine = MockEngine {
            respond(
                content = """
                [
                    {
                        "accessoryInformation": {
                            "Name": "@@@ My Air-Purifier @@@"
                        },
                        "values": {
                            "StatusActive": 0,
                            "PM2_5Density": 0
                        }
                    }
                ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val poller = AirQualityPoller(
            interval = interval,
            offset = offset,
            homebridgeUrl = HOMEBRIDGE_URL,
            homebridgeClient = HttpClient(mockHomebridgeEngine),
            metricsSender = dummyMetricsSender(),
            jsonSerializer = mainJsonSerializer
        )

        // When
        val metrics = poller.fetchAirQualityData()

        // Then
        assertNull(metrics)
    }

    @Test
    fun `fetchAirQualityData() returns null when Homebridge returns 500`() = runTest {
        // Given
        val mockHomebridgeEngine = MockEngine {
            respond("Internal Server Error", HttpStatusCode.InternalServerError)
        }

        val poller = AirQualityPoller(
            interval = interval,
            offset = offset,
            homebridgeUrl = HOMEBRIDGE_URL,
            homebridgeClient = HttpClient(mockHomebridgeEngine),
            metricsSender = dummyMetricsSender(),
            jsonSerializer = mainJsonSerializer
        )

        // When
        val metrics = poller.fetchAirQualityData()

        // Then
        assertNull(metrics)
    }
}