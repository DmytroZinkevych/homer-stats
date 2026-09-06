package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
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

private val HTTP_SERVER_ENGINE_FACTORY = io.ktor.server.cio.CIO
private val HTTP_CLIENT_ENGINE_FACTORY = io.ktor.client.engine.cio.CIO

private const val HOMEBRIDGE_URL = "http://localhost:8581"
private const val HOMEBRIDGE_USER_VAR = "HOMEBRIDGE_USER"
private const val HOMEBRIDGE_PASSWORD_ENV_VAR = "HOMEBRIDGE_PASSWORD"

private val AIR_QUALITY_POLLING_INTERVAL = 5.minutes

val mainJsonSerializer = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun main() {
    embeddedServer(
        factory = HTTP_SERVER_ENGINE_FACTORY,
        port = SERVER_PORT,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val httpClient = HttpClient(HTTP_CLIENT_ENGINE_FACTORY) {
        configTimeouts()
        configLogging()
    }

    val metricsSender = MetricsSender(VM_IMPORT_URL, httpClient, mainJsonSerializer)

    val homebridgeClient: HttpClient = HomebridgeClientProvider(
        homebridgeUrl = HOMEBRIDGE_URL,
        user = getEnvVar(HOMEBRIDGE_USER_VAR),
        password = getEnvVar(HOMEBRIDGE_PASSWORD_ENV_VAR),
        jsonSerializer = mainJsonSerializer,
        httpClientEngineFactory = HTTP_CLIENT_ENGINE_FACTORY
    ).createClient()

    val airQualityPoller = AirQualityPoller(
        interval = AIR_QUALITY_POLLING_INTERVAL,
        homebridgeUrl = HOMEBRIDGE_URL,
        homebridgeClient = homebridgeClient,
        metricsSender = metricsSender,
        jsonSerializer = mainJsonSerializer,
    )

    monitor.subscribe(ApplicationStopped) {
        airQualityPoller.close()
        httpClient.close()
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
