package io.github.dmytrozinkevych.homerstats

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.bodylimit.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

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
