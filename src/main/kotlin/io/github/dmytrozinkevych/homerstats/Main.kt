package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.owasp.encoder.Encode
import org.slf4j.event.Level

private val logger = KotlinLogging.logger {}

private const val PORT = 8000
private const val VM_IMPORT_URL = "http://localhost:8428/api/v1/import"
private const val MAX_PAYLOAD_BYTES = 65_536L // 64 KB

val mainJsonSerializer = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun main() {
    embeddedServer(
        factory = io.ktor.server.cio.CIO,
        port = PORT,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val httpClient = HttpClient(io.ktor.client.engine.cio.CIO)
    val metricsSender = MetricsSender(VM_IMPORT_URL, httpClient, mainJsonSerializer)

    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }

    install(CallLogging) {
        level = Level.INFO
    }

    configureClimateTelemetryRoute(metricsSender, mainJsonSerializer)
}

fun Application.configureClimateTelemetryRoute(
    metricsSender: MetricsSender,
    jsonSerializer: Json
) {
    routing {
        post("/api/climate-telemetry") {
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_PAYLOAD_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, "Payload exceeds limit")
                return@post
            }
            val rawText = call.receiveText()
            if (rawText.length > MAX_PAYLOAD_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, "Payload exceeds limit")
                return@post
            }

            val payload = try {
                jsonSerializer.decodeFromString<ClimateTelemetryPayload>(rawText)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.warn(e) {
                    "Failed to parse climate telemetry payload. Raw body: '${Encode.forJava(rawText)}'"
                }
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON payload")
                return@post
            }
            logger.info { "Received climate telemetry: $payload" }

            try {
                val response = metricsSender.sendMetrics(payload.toMetrics())
                if (response.status.isSuccess()) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    logger.error { "Sending metrics to VictoriaMetrics failed with status: ${response.status}" }
                    call.respond(HttpStatusCode.InternalServerError, "Failed to persist metrics")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.error(e) { "Failed to persist metrics" }
                call.respond(HttpStatusCode.InternalServerError, "Failed to persist metrics")
            }
        }
    }
}
