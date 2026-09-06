package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.owasp.encoder.Encode
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

private const val SERVER_PORT = 8000
private const val VM_IMPORT_URL = "http://localhost:8428/api/v1/import"
private const val MAX_PAYLOAD_BYTES = 65_536L // 64 KB

private val HTTP_SERVER_ENGINE = io.ktor.server.cio.CIO
private val HTTP_CLIENT_ENGINE = io.ktor.client.engine.cio.CIO

private const val HOMEBRIDGE_URL = "http://localhost:8581"
private val HOMEBRIDGE_USER = getEnvVar("HOMEBRIDGE_USER")
private val HOMEBRIDGE_PASSWORD = getEnvVar("HOMEBRIDGE_PASSWORD")

private val AIR_QUALITY_POLLING_INTERVAL = 5.minutes

val mainJsonSerializer = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun main() {
    embeddedServer(
        factory = HTTP_SERVER_ENGINE,
        port = SERVER_PORT,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val httpClient = HttpClient(HTTP_CLIENT_ENGINE) {
        configTimeouts()
        configLogging()
    }
    val metricsSender = MetricsSender(VM_IMPORT_URL, httpClient, mainJsonSerializer)
    val airQualityPoller = AirQualityPoller(
        homebridgeUrl = HOMEBRIDGE_URL,
        user = HOMEBRIDGE_USER,
        password = HOMEBRIDGE_PASSWORD,
        interval = AIR_QUALITY_POLLING_INTERVAL,
        httpClientEngine = HTTP_CLIENT_ENGINE.create(),
        jsonSerializer = mainJsonSerializer,
        metricsSender = metricsSender
    )

    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        airQualityPoller.close()
    }

    install(RequestBodyLimit) {
        bodyLimit {
            MAX_PAYLOAD_BYTES
        }
    }

    configureClimateTelemetryRoute(metricsSender, mainJsonSerializer)

    airQualityPoller.startPolling(this)
}

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
