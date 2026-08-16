package io.github.dmytrozinkevych.homerstats.model

import kotlinx.serialization.Serializable

@Serializable
data class ClimateTelemetryPayload(
    val timestamp: String, // ISO 8601 format
    val temperature: Float,
    val humidity: Int
)
