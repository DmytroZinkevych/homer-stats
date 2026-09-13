package io.github.dmytrozinkevych.homerstats

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Config {
    val HTTP_SERVER_ENGINE_FACTORY = io.ktor.server.cio.CIO
    val HTTP_CLIENT_ENGINE_FACTORY = io.ktor.client.engine.cio.CIO

    const val SERVER_PORT = 8000
    const val SERVER_MAX_REQUEST_BODY_BYTES = 65_536L // 64 KB

    const val REQUEST_TIMEOUT_MS = 10_000L
    const val CONNECT_TIMEOUT_MS = 5_000L
    const val SOCKET_TIMEOUT_MS = 5_000L

    const val METRICS_IMPORT_URL = "http://localhost:8428/api/v1/import"
    const val HOMEBRIDGE_URL = "http://localhost:8581"

    const val API_KEY_HEADER_NAME = "X-API-Key"

    const val API_KEY_ENV_VAR = "HOMER_STATS_API_KEY"   // Generate with kotlin-notebook.ipynb
    const val HOMEBRIDGE_USER_ENV_VAR = "HOMEBRIDGE_USER"
    const val HOMEBRIDGE_PASSWORD_ENV_VAR = "HOMEBRIDGE_PASSWORD"

    val POLLING_INTERVAL = 5.minutes
    val POLLING_OFFSET = 5.seconds
}