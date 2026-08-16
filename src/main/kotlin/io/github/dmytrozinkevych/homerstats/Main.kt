package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

private const val PORT = 8000
private const val VM_IMPORT_URL = "http://localhost:8428/api/v1/import"

private val metricsSender = MetricsSender(VM_IMPORT_URL)

fun main() {
    embeddedServer(CIO, port = PORT) {
        // Enable JSON deserialization
        install(ContentNegotiation) {
            json()
        }

        routing {
            post ("/api/climate-telemetry") {
                val payload = call.receive<ClimateTelemetryPayload>()
                with (payload) {
                    println("Received metrics: Timestamp=$timestamp Temperature=$temperature°C, Humidity=$humidity%")
                }
                val epochMillis = try {
                    Instant.parse(payload.timestamp).toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
                val tempMetric = VmMetricSeries(
                    metric = mapOf(
                        "__name__" to "home_temperature_celsius"
                    ),
                    values = listOf(payload.temperature),
                    timestamps = listOf(epochMillis)
                )
                val humidityMetric = VmMetricSeries(
                    metric = mapOf(
                        "__name__" to "home_humidity_percents"
                    ),
                    values = listOf(payload.humidity.toFloat()),
                    timestamps = listOf(epochMillis)
                )
                try {
                    val response = metricsSender.sendMetrics(listOf(tempMetric, humidityMetric))
                    println("Successfully sent metrics to VictoriaMetrics: $response")
                    if (response.status.isSuccess()) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(response.status)
                    }
                } catch (e: Exception) {
                    println("Failed to send metrics: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to send metrics")
                }
            }
        }
    }.start(wait = true)
}