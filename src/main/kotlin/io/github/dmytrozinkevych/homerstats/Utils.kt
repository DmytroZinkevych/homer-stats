package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.dmytrozinkevych.homerstats.model.MetricSeries
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import java.time.Instant

private const val REQUEST_TIMEOUT_MS = 10_000L
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val SOCKET_TIMEOUT_MS = 5_000L

private const val LOCATION_FIELD = "location"
private const val SOURCE_FIELD = "source"

private const val TEMPERATURE_METRIC_NAME = "temperature_celsius"
private const val HUMIDITY_METRIC_NAME = "humidity_percents"
private const val PM_2_5_METRIC_NAME = "pm_2_5_density"

private const val AIR_QUALITY_HOME_LOCATION = "indoor"

private const val MAX_FIELD_LENGTH = 50

fun getEnvVar(name: String): String = checkNotNull(System.getenv(name)) {
    "Required environment variable '$name' is missing"
}

fun HttpClientConfig<*>.configTimeouts() {
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }
}

fun HttpClientConfig<*>.configLogging() {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.INFO
    }
}

fun ClimateTelemetryPayload.toMetrics(): List<MetricSeries> {
    val epochMillis = this.timestamp.toEpochMilli()
    val temperatureMetric = MetricSeries(
        metricName = TEMPERATURE_METRIC_NAME,
        value = this.temperature,
        timestamp = epochMillis,
        LOCATION_FIELD to this.location,
        SOURCE_FIELD to this.source
    )
    val humidityMetric = MetricSeries(
        metricName = HUMIDITY_METRIC_NAME,
        value = this.humidity.toFloat(),
        timestamp = epochMillis,
        LOCATION_FIELD to this.location,
        SOURCE_FIELD to this.source
    )
    return listOf(temperatureMetric, humidityMetric)
}

fun Float.toPm25Metric(timestamp: Long, source: String): List<MetricSeries> = listOf(
    MetricSeries(
        metricName = PM_2_5_METRIC_NAME,
        value = this,
        timestamp = timestamp,
        LOCATION_FIELD to AIR_QUALITY_HOME_LOCATION,
        SOURCE_FIELD to source
    )
)

fun String.toEpochMilli(): Long = try {
    Instant.parse(this).toEpochMilli()
} catch (_: Exception) {
    Instant.now().toEpochMilli()
}

fun validateTextField(fieldName: String, fieldValue: String) {
    require(fieldValue.isNotBlank()) {
        "'$fieldName' field cannot be blank"
    }
    require(!fieldValue.containsNewlines()) {
        "'$fieldName' field cannot contain newlines"
    }
    require(fieldValue.length <= MAX_FIELD_LENGTH) {
        "'$fieldName' field exceeds $MAX_FIELD_LENGTH characters"
    }
}

fun String.containsNewlines() =
    this.contains('\n') || this.contains('\r')
