package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.dmytrozinkevych.homerstats.model.MetricSeries
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import java.time.Instant

private const val TEMPERATURE_METRIC_NAME = "temperature_celsius"
private const val HUMIDITY_METRIC_NAME = "humidity_percents"
private const val PM_2_5_METRIC_NAME = "pm_2_5_density"

private const val LOCATION_FIELD = "location"
private const val SOURCE_FIELD = "source"

private const val AIR_QUALITY_HOME_LOCATION = "indoor"

private const val MAX_FIELD_LENGTH = 50

private val CAMEL_CASE_REGEX = Regex("([a-z])([A-Z])")
private val LETTER_TO_DIGIT_REGEX = Regex("([a-z])([0-9])") // expects lowercased input
private val DIGIT_TO_LETTER_REGEX = Regex("([0-9])([a-z])") // expects lowercased input
private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+") // expects lowercased input

fun getEnvVar(name: String): String = checkNotNull(System.getenv(name)) {
    "Required environment variable '$name' is missing"
}

fun HttpClientConfig<*>.configTimeouts() {
    install(HttpTimeout) {
        requestTimeoutMillis = Config.REQUEST_TIMEOUT_MS
        connectTimeoutMillis = Config.CONNECT_TIMEOUT_MS
        socketTimeoutMillis = Config.SOCKET_TIMEOUT_MS
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
        LOCATION_FIELD to this.location.formatForMetric(),
        SOURCE_FIELD to this.source.formatForMetric()
    )
    val humidityMetric = MetricSeries(
        metricName = HUMIDITY_METRIC_NAME,
        value = this.humidity.toFloat(),
        timestamp = epochMillis,
        LOCATION_FIELD to this.location.formatForMetric(),
        SOURCE_FIELD to this.source.formatForMetric()
    )
    return listOf(temperatureMetric, humidityMetric)
}

fun Float.toPm25Metric(timestamp: Long, source: String): List<MetricSeries> = listOf(
    MetricSeries(
        metricName = PM_2_5_METRIC_NAME,
        value = this,
        timestamp = timestamp,
        LOCATION_FIELD to AIR_QUALITY_HOME_LOCATION.formatForMetric(),
        SOURCE_FIELD to source.formatForMetric()
    )
)

fun String.toEpochMilli(): Long = try {
    Instant.parse(this).toEpochMilli()
} catch (_: Exception) {
    Instant.now().toEpochMilli()
}

fun String?.formatForMetric(defaultIfBlank: String = "unknown"): String =
    this
        ?.replace(CAMEL_CASE_REGEX, "$1_$2")
        ?.lowercase()
        ?.replace(LETTER_TO_DIGIT_REGEX, "$1_$2")
        ?.replace(DIGIT_TO_LETTER_REGEX, "$1_$2")
        ?.replace(NON_ALPHANUMERIC_REGEX, "_")
        ?.trim('_')
        ?.ifBlank { defaultIfBlank }
        ?: defaultIfBlank

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
