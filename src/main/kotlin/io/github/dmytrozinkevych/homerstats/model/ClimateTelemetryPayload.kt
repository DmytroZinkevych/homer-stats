package io.github.dmytrozinkevych.homerstats.model

import io.github.dmytrozinkevych.homerstats.validateTextField
import kotlinx.serialization.Serializable

@Serializable
data class ClimateTelemetryPayload(
    val timestamp: String,  // ISO 8601 format
    val location: String,
    val source: String,
    val temperature: Float, // Celsius
    val humidity: Int       // Relative humidity, percents
) {
    init {
        validateTextField("timestamp", timestamp)
        validateTextField("location", location)
        validateTextField("source", source)

        require(temperature in -100.0f..100.0f) {
            "Temperature out of realistic bounds"
        }
        require(humidity in 0..100) {
            "Humidity out of realistic bounds"
        }
    }
}
