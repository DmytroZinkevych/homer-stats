package io.github.dmytrozinkevych.homerstats

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ClimateTelemetryPayload(
    val timestamp: String,
    val temperature: Float,
    val humidity: Int
)

fun main() {
    embeddedServer(Netty, port = 8000) {
        // Enable JSON deserialization
        install(ContentNegotiation) {
            json()
        }

        routing {
            post ("/api/climate-telemetry") {
                val payload = call.receive<ClimateTelemetryPayload>()
                with (payload) {
                    println("[$timestamp] Recorded: Temperature=$temperature°C, Humidity=$humidity%")
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }.start(wait = true)
}