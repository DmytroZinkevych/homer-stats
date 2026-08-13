package io.github.dmytrozinkevych.homerstats

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ClimateTelemetryPayload(
    val timestamp: String,
    val temperature: Float,
    val humidity: Int
)

private const val PORT = 8000;

private val telemetryFile = File("telemetry_data.jsonl")

fun main() {
    embeddedServer(CIO, port = PORT) {
        // Enable JSON deserialization
        install(ContentNegotiation) {
            json()
        }

        routing {
            post ("/api/climate-telemetry") {
                val payload = call.receive<ClimateTelemetryPayload>()

                val jsonLine = Json.encodeToString(payload) + "\n"
                synchronized(telemetryFile) {
                    telemetryFile.appendText(jsonLine)
                }

                with (payload) {
                    println("[$timestamp] Recorded: Temperature=$temperature°C, Humidity=$humidity%")
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }.start(wait = true)
}