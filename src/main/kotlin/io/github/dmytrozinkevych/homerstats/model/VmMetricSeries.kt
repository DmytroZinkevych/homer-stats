package io.github.dmytrozinkevych.homerstats.model

import kotlinx.serialization.Serializable

// Matches VictoriaMetrics /api/v1/import endpoint
@Serializable
data class VmMetricSeries(
    val metric: Map<String, String>,
    val values: List<Float>,
    val timestamps: List<Long> // Epoch milliseconds
)
