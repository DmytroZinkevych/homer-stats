package io.github.dmytrozinkevych.homerstats.model

import kotlinx.serialization.Serializable

private const val METRIC_NAME_FIELD = "__name__"

// Matches VictoriaMetrics /api/v1/import endpoint
@Serializable
data class VmMetricSeries(
    val metric: Map<String, String>,
    val values: List<Float>,
    val timestamps: List<Long> // Epoch milliseconds
) {
    constructor(
        metricName: String,
        value: Float,
        timestamp: Long,
        vararg otherParams: Pair<String, String>
    ) : this(
        metric = mapOf(METRIC_NAME_FIELD to metricName, *otherParams),
        values = listOf(value),
        timestamps = listOf(timestamp)
    )
}
