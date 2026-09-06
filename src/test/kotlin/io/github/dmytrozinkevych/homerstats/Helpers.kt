package io.github.dmytrozinkevych.homerstats

import io.ktor.client.engine.*

fun HttpClientEngine.asFactory() = object : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return this@asFactory
    }
}