package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class MetricsSender(
    private val url: String,
    private val httpClient: HttpClient,
    private val jsonSerializer: Json
) {
    internal suspend fun sendMetrics(metrics: List<VmMetricSeries>): HttpResponse {
        val ndjsonPayload = metrics
            .joinToString("\n") {
                jsonSerializer.encodeToString(it)
            }
        logger.info { "Sending metrics:\n$ndjsonPayload" }
        return httpClient.post(url) {
            contentType(ContentType.parse("application/stream+json"))
            setBody(ndjsonPayload)
        }
    }

    suspend fun sendAndVerify(metrics: List<VmMetricSeries>): Boolean =
        try {
            val response = this.sendMetrics(metrics)
            val isSuccess = response.status.isSuccess()
            if (!isSuccess) {
                logger.error { "Sending metrics to VictoriaMetrics failed with status: ${response.status}" }
            }
            isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist metrics" }
            false
        }
}
