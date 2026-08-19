package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

private val logger = KotlinLogging.logger {}

private const val PORT = 8000
private const val VM_IMPORT_URL = "http://localhost:8428/api/v1/import"

fun main() {
    embeddedServer(
        factory = io.ktor.server.cio.CIO,
        port = PORT,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val httpClient = HttpClient(io.ktor.client.engine.cio.CIO)
    val metricsSender = MetricsSender(VM_IMPORT_URL, httpClient)

    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        // Enable JSON deserialization
        json(Json {
            ignoreUnknownKeys = true
        })
    }

    configureClimateTelemetryRoute(metricsSender)
}

fun Application.configureClimateTelemetryRoute(metricsSender: MetricsSender) {
    routing {
        post("/api/climate-telemetry") {
            val payload = call.receive<ClimateTelemetryPayload>()
            logger.info { "Received climate telemetry: $payload" }
            val metrics = payload.toMetrics()
            try {
                val response = metricsSender.sendMetrics(metrics)
                if (response.status.isSuccess()) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(response.status)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send metrics" }
                call.respond(HttpStatusCode.InternalServerError, "Failed to send metrics")
            }
        }
    }
}
