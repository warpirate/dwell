package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterWeatherTest {

    @Test
    fun `every condition round-trips through encode and decode`() {
        WeatherCondition.entries.forEach { condition ->
            listOf(0, 1, 37, 70, 100).forEach { intensity ->
                val original = PosterWeather(condition, intensity)
                val decoded = PosterWeather.decode(original.encode())
                assertEquals("condition mismatch for $condition@$intensity", original, decoded)
            }
        }
    }

    @Test
    fun `decode falls back to Default on garbage input`() {
        assertEquals(PosterWeather.Default, PosterWeather.decode(""))
        assertEquals(PosterWeather.Default, PosterWeather.decode("not|even|close|to|valid"))
        assertEquals(PosterWeather.Default, PosterWeather.decode("BOGUS_CONDITION|70"))
        assertEquals(PosterWeather.Default, PosterWeather.decode("RAIN|not-a-number"))
    }

    @Test
    fun `coerced clamps intensity into range`() {
        assertEquals(0, PosterWeather(WeatherCondition.RAIN, -20).coerced().intensity)
        assertEquals(100, PosterWeather(WeatherCondition.RAIN, 500).coerced().intensity)
    }

    @Test
    fun `default is clear at 70 percent`() {
        assertEquals(WeatherCondition.CLEAR, PosterWeather.Default.condition)
        assertEquals(70, PosterWeather.Default.intensity)
    }
}
