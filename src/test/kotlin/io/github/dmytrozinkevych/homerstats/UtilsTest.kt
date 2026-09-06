package io.github.dmytrozinkevych.homerstats

import io.github.dmytrozinkevych.homerstats.model.ClimateTelemetryPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UtilsTest {
    @Test
    fun `converts payload to metric series`() {
        val payload = ClimateTelemetryPayload("2026-08-17T19:41:33+02:00", "indoor", "homepod", 22.5f, 49)
        val metrics = payload.toMetrics()

        assertEquals(2, metrics.size)

        val temperatureMetric = metrics[0]
        assertEquals("temperature_celsius", temperatureMetric.metric["__name__"])
        assertEquals("indoor", temperatureMetric.metric["location"])
        assertEquals("homepod", temperatureMetric.metric["source"])
        assertEquals(listOf(22.5f), temperatureMetric.values)
        assertEquals(listOf(1786988493000L), temperatureMetric.timestamps)

        val humidityMetric = metrics[1]
        assertEquals("humidity_percents", humidityMetric.metric["__name__"])
        assertEquals("indoor", humidityMetric.metric["location"])
        assertEquals("homepod", humidityMetric.metric["source"])
        assertEquals(listOf(49.0f), humidityMetric.values)
        assertEquals(listOf(1786988493000L), humidityMetric.timestamps)
    }

    @Test
    fun `falls back to current time on invalid timestamp string`() {
        val beforeTest = System.currentTimeMillis()

        val payload = ClimateTelemetryPayload("invalid-date-string", "indoor", "homepod", 22.5f, 49)
        val metrics = payload.toMetrics()

        val afterTest = System.currentTimeMillis()

        val generatedTimestamp = metrics[0].timestamps.first()
        assertTrue(generatedTimestamp in beforeTest..afterTest)
    }

    @Test
    fun `formats various strings correctly for metric source labels`() {
        assertEquals("my_air_purifier_3_c_a_4", "@@@ My Air_Purifier 3C A4 ~~~".formatForMetric())
        assertEquals("user_s_air_purifier_3_c", "User's AirPurifier3C".formatForMetric())
        assertEquals("living_room_sensor_01", "livingRoom___Sensor01".formatForMetric())

        assertEquals("unknown", null.formatForMetric())
        assertEquals("unknown", "".formatForMetric())
        assertEquals("unknown", "@@@ ~~~ ###".formatForMetric())
    }
}
