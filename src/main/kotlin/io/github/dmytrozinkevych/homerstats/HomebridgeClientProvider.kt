package io.github.dmytrozinkevych.homerstats

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val logger = KotlinLogging.logger {}

private const val LOGIN_ENDPOINT = "/api/auth/login"
private const val ACCESS_TOKEN_FIELD = "access_token"

@Serializable
private data class AuthRequest(val username: String, val password: String)

class HomebridgeClientProvider(
    private val homebridgeUrl: String,
    private val user: String,
    private val password: String,
    private val jsonSerializer: Json,
    private val httpClientEngine: HttpClientEngine
) {
    fun createClient(): HttpClient {
        return HttpClient(httpClientEngine) {
            configTimeouts()

            configLogging()

            install(Auth) {
                bearer {
                    loadTokens {
                        fetchToken()
                    }

                    refreshTokens {
                        fetchToken()
                    }
                }
            }
        }
    }

    private suspend fun fetchToken(): BearerTokens? {
        // Separate unauthenticated client for auth calls to avoid infinite loops
        HttpClient(httpClientEngine) {
            configTimeouts()
            configLogging()
        }.use { authClient ->
            return try {
                val response = authClient.post(homebridgeUrl.trimEnd('/') + LOGIN_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(AuthRequest(user, password))
                }
                if (response.status.isSuccess()) {
                    val token = jsonSerializer.parseToJsonElement(response.bodyAsText())
                        .jsonObject[ACCESS_TOKEN_FIELD]
                        ?.jsonPrimitive
                        ?.contentOrNull
                    if (token != null) {
                        BearerTokens(accessToken = token, refreshToken = "")
                    } else {
                        logger.warn { "Homebridge login succeeded but '$ACCESS_TOKEN_FIELD' field was missing" }
                        null
                    }
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
}
