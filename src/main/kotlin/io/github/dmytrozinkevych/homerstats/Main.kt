package io.github.dmytrozinkevych.homerstats

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.bodylimit.*
import kotlinx.serialization.json.Json

val mainJsonSerializer = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun main() {
    embeddedServer(
        factory = Config.HTTP_SERVER_ENGINE_FACTORY,
        port = Config.SERVER_PORT,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val httpClient = HttpClient(Config.HTTP_CLIENT_ENGINE_FACTORY) {
        configTimeouts()
        configLogging()
    }

    val metricsSender = MetricsSender(Config.METRICS_IMPORT_URL, httpClient, mainJsonSerializer)

    val homebridgeClient: HttpClient = HomebridgeClientProvider(
        homebridgeUrl = Config.HOMEBRIDGE_URL,
        user = getEnvVar(Config.HOMEBRIDGE_USER_VAR),
        password = getEnvVar(Config.HOMEBRIDGE_PASSWORD_ENV_VAR),
        jsonSerializer = mainJsonSerializer,
        httpClientEngineFactory = Config.HTTP_CLIENT_ENGINE_FACTORY
    ).createClient()

    val airQualityPoller = AirQualityPoller(
        interval = Config.POLLING_INTERVAL,
        offset = Config.POLLING_OFFSET,
        homebridgeUrl = Config.HOMEBRIDGE_URL,
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
            Config.SERVER_MAX_REQUEST_BODY_BYTES
        }
    }

    configureClimateTelemetryRoute(metricsSender, mainJsonSerializer)

    airQualityPoller.startPolling(this)
}
