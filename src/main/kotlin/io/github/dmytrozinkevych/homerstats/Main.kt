package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val PORT = 8000
private const val VM_IMPORT_URL = "http://localhost:8428/api/v1/import"

private val httpClient = HttpClient(io.ktor.client.engine.cio.CIO)
private val metricsSender = MetricsSender(VM_IMPORT_URL, httpClient)

fun main() {
    embeddedServer(io.ktor.server.cio.CIO, port = PORT) {
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
                val metrics = payload.toMetrics()
                try {
                    val response = metricsSender.sendMetrics(metrics)
                    val status = response.status
                    println("Successfully sent metrics to VictoriaMetrics: $status")
                    if (status.isSuccess()) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(status)
                    }
                } catch (e: Exception) {
                    println("Failed to send metrics: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to send metrics")
                }
            }
        }

        monitor.subscribe(ApplicationStopped) {
            httpClient.close()
        }
    }.start(wait = true)
}