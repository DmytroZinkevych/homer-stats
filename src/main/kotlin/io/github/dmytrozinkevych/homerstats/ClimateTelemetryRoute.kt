package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.owasp.encoder.Encode

private val logger = KotlinLogging.logger {}

fun Application.configureClimateTelemetryRoute(
    metricsSender: MetricsSender,
    jsonSerializer: Json
) {
    routing {
        post("/api/climate-telemetry") {
            val payload = parsePayload(call.receiveText(), jsonSerializer)
            if (payload == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON payload")
                return@post
            }
            logger.info { "Received climate telemetry: $payload" }

            val isPersisted = metricsSender.sendAndVerify(payload.toMetrics())
            if (isPersisted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to persist metrics")
            }
        }
    }
}

private fun parsePayload(rawText: String, jsonSerializer: Json): ClimateTelemetryPayload? =
    try {
        jsonSerializer.decodeFromString<ClimateTelemetryPayload>(rawText)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(e) {
            "Failed to parse climate telemetry payload. Raw body: '${Encode.forJava(rawText)}'"
        }
        null
    }