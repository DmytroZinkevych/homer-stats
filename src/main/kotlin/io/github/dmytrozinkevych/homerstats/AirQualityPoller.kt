package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

private const val ACCESSORIES_ENDPOINT = "/api/accessories"
private const val PM_2_5_DENSITY_FIELD = "PM2_5Density"
private const val STATUS_ACTIVE = "StatusActive"

class AirQualityPoller(
    private val homebridgeUrl: String,
    user: String,
    password: String,
    private val interval: Duration,
    httpClientEngine: HttpClientEngineFactory<HttpClientEngineConfig>,
    jsonSerializer: Json,
    private val metricsSender: MetricsSender,
    private val homebridgeClient: HttpClient = HomebridgeClientProvider(
        homebridgeUrl,
        user,
        password,
        jsonSerializer,
        httpClientEngine
    ).createClient()
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
                delay(interval)
            }
        }
    }

    private suspend fun fetchAirQualityData(): List<VmMetricSeries>? {
        val response = homebridgeClient.get(homebridgeUrl + ACCESSORIES_ENDPOINT)
        val isSuccess = response.status.isSuccess()
        if (!isSuccess) {
            logger.warn { "Couldn't connect to homebridge, status: ${response.status}" }
            return null
        }
        val jsonString = response.bodyAsText()
        val pm25Value = mainJsonSerializer.parseToJsonElement(jsonString)
            .jsonArray
            .asSequence()
            .map { it.jsonObject }
            .mapNotNull { it["values"] }
            .map { it.jsonObject }
            .firstOrNull { it.containsKey(PM_2_5_DENSITY_FIELD) && it[STATUS_ACTIVE]?.jsonPrimitive?.intOrNull == 1 }
            ?.get(PM_2_5_DENSITY_FIELD)
            ?.jsonPrimitive
            ?.floatOrNull

        if (pm25Value == null) {
            logger.warn { "Couldn't get PM 2.5 value from homebridge data - device may be disconnected" }
            return null
        }
        val timestamp = response.responseTime.timestamp
        logger.info { "Fetched PM 2.5 value: $pm25Value, timestamp: $timestamp" }
        return pm25Value.toPm25Metric(timestamp)
    }
}
