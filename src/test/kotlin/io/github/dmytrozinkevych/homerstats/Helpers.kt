package io.github.dmytrozinkevych.homerstats

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*

fun HttpClientEngine.asFactory() = object : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return this@asFactory
    }
}

fun dummyMetricsSender() = MetricsSender(
    url = "http://localhost:8428",
    httpClient = HttpClient(MockEngine { error("No HTTP calls should be made") }),
    jsonSerializer = mainJsonSerializer
)