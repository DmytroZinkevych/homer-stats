package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class MetricsSender(
    private val url: String,
    private val httpClient: HttpClient,
    private val jsonSerializer: Json
) {
    suspend fun sendMetrics(metrics: List<VmMetricSeries>): HttpResponse {
        val ndjsonPayload = metrics
            .joinToString("\n") {
                jsonSerializer.encodeToString(it)
            }
        return httpClient.post(url) {
            contentType(ContentType.parse("application/stream+json"))
            setBody(ndjsonPayload)
        }
    }
}
