package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import io.github.dmytrozinkevych.homerstats.model.VmMetricSeries
import java.time.Instant

private const val METRIC_NAME_FIELD = "__name__"
private const val LOCATION_FIELD = "location"
private const val SOURCE_FIELD = "source"

private const val TEMPERATURE_METRIC_NAME = "temperature_celsius"
private const val HUMIDITY_METRIC_NAME = "humidity_percents"

fun ClimateTelemetryPayload.toMetrics(): List<VmMetricSeries> {
    val epochMillis = this.timestamp.toEpochMilli()
    val temperatureMetric = VmMetricSeries(
        metric = mapOf(
            METRIC_NAME_FIELD to TEMPERATURE_METRIC_NAME,
            LOCATION_FIELD to this.location,
            SOURCE_FIELD to this.source
        ),
        values = listOf(this.temperature),
        timestamps = listOf(epochMillis)
    )
    val humidityMetric = VmMetricSeries(
        metric = mapOf(
            METRIC_NAME_FIELD to HUMIDITY_METRIC_NAME,
            LOCATION_FIELD to this.location,
            SOURCE_FIELD to this.source
        ),
        values = listOf(this.humidity.toFloat()),
        timestamps = listOf(epochMillis)
    )
    return listOf(temperatureMetric, humidityMetric)
}

fun String.toEpochMilli(): Long = try {
    Instant.parse(this).toEpochMilli()
} catch (_: Exception) {
    System.currentTimeMillis()
}
