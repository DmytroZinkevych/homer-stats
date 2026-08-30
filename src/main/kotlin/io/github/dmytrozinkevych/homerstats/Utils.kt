package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import java.time.Instant

private const val LOCATION_FIELD = "location"
private const val SOURCE_FIELD = "source"

private const val TEMPERATURE_METRIC_NAME = "temperature_celsius"
private const val HUMIDITY_METRIC_NAME = "humidity_percents"

private const val MAX_FIELD_LENGTH = 50

fun ClimateTelemetryPayload.toMetrics(): List<VmMetricSeries> {
    val epochMillis = this.timestamp.toEpochMilli()
    val temperatureMetric = VmMetricSeries(
        metricName = TEMPERATURE_METRIC_NAME,
        value = this.temperature,
        timestamp = epochMillis,
        LOCATION_FIELD to this.location,
        SOURCE_FIELD to this.source
    )
    val humidityMetric = VmMetricSeries(
        metricName = HUMIDITY_METRIC_NAME,
        value = this.humidity.toFloat(),
        timestamp = epochMillis,
        LOCATION_FIELD to this.location,
        SOURCE_FIELD to this.source
    )
    return listOf(temperatureMetric, humidityMetric)
}

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
