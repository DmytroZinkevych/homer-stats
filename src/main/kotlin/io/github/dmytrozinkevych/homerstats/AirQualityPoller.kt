package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.MetricSeries
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.owasp.encoder.Encode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private const val ACCESSORIES_ENDPOINT = "/api/accessories"

private const val VALUES_FIELD = "values"
private const val ACCESSORY_INFORMATION_FIELD = "accessoryInformation"
private const val PM_2_5_DENSITY_FIELD = "PM2_5Density"
private const val STATUS_ACTIVE_FIELD = "StatusActive"
private const val NAME_FIELD = "Name"

private const val DEFAULT_SOURCE_NAME = "air_quality_sensor"

private val marginMillis = 5.seconds.inWholeMilliseconds

class AirQualityPoller(
    private val interval: Duration,
    private val homebridgeUrl: String,
    private val homebridgeClient: HttpClient,
    private val metricsSender: MetricsSender,
    private val jsonSerializer: Json,
) : AutoCloseable by homebridgeClient {

    fun startPolling(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val metrics = try {
                    fetchAirQualityData()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Failed to fetch air quality data" }
                    null
                }
                if (metrics != null) {
                    metricsSender.sendAndVerify(metrics)
                }
                delay(calculateDelay())
            }
        }
    }

    private fun calculateDelay(): Duration {
        val intervalMillis = interval.inWholeMilliseconds
        val nowMillis = System.currentTimeMillis()

        // preventing drift from fixed delays to end up on the exact clock boundary (e.g. 12:05:00 after 12:00:00)
        val untilBoundary = intervalMillis - (nowMillis % intervalMillis)

        var delay = untilBoundary - marginMillis
        if (untilBoundary <= marginMillis) {
            // skipping this iteration since we're already on the new one
            delay += intervalMillis
        }
        return delay.milliseconds
    }

    internal suspend fun fetchAirQualityData(): List<MetricSeries>? {
        val response = homebridgeClient.get(homebridgeUrl.trimEnd('/') + ACCESSORIES_ENDPOINT)
        if (!response.status.isSuccess()) {
            logger.warn { "Couldn't connect to homebridge, status: ${response.status}" }
            return null
        }
        val targetAccessory = jsonSerializer.parseToJsonElement(response.bodyAsText())
            .jsonArray
            .asSequence()
            .map { it.jsonObject }
            .firstOrNull { accessory ->
                val values = accessory[VALUES_FIELD]?.jsonObject
                values?.containsKey(PM_2_5_DENSITY_FIELD) == true
                        && values[STATUS_ACTIVE_FIELD]?.jsonPrimitive?.intOrNull == 1
            }

        if (targetAccessory == null) {
            logger.warn { "Couldn't find an active device with PM 2.5 data - device may be disconnected" }
            return null
        }

        val pm25Value = targetAccessory[VALUES_FIELD]
            ?.jsonObject
            ?.get(PM_2_5_DENSITY_FIELD)
            ?.jsonPrimitive
            ?.floatOrNull

        if (pm25Value == null) {
            logger.warn { "PM 2.5 density value was missing or non-numeric" }
            return null
        }

        val sourceName = targetAccessory[ACCESSORY_INFORMATION_FIELD]
            ?.jsonObject
            ?.get(NAME_FIELD)
            ?.jsonPrimitive
            ?.contentOrNull
            ?: DEFAULT_SOURCE_NAME

        val timestamp = response.responseTime.timestamp
        logger.info { "Fetched PM 2.5 value: $pm25Value for device '${Encode.forJava(sourceName)}', timestamp: $timestamp" }

        return pm25Value.toPm25Metric(timestamp, sourceName)
    }
}
