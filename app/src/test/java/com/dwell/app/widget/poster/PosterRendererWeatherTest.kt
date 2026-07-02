package com.dwell.app.widget.poster

import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PosterRendererWeatherTest {

    private val hours = listOf(7f, 12f, 17.6f, 22f) // dawn, noon, golden hour, night

    @Test
    fun `every condition renders at every hour without throwing`() {
        WeatherCondition.entries.forEach { condition ->
            hours.forEach { hour ->
                val weather = PosterWeather(condition, 70)
                val bmp = PosterRenderer.render(64, 64, hour, weather)
                assertEquals(64, bmp.width)
                assertEquals(64, bmp.height)
            }
        }
    }

    @Test
    fun `clear weather is the default and matches the no-weather call`() {
        val hour = 15f
        val withDefaultParam = PosterRenderer.render(48, 48, hour)
        val withExplicitClear = PosterRenderer.render(48, 48, hour, PosterWeather(WeatherCondition.CLEAR, 70))
        val sampleX = 24
        val sampleY = 24
        assertEquals(
            withDefaultParam.getPixel(sampleX, sampleY),
            withExplicitClear.getPixel(sampleX, sampleY),
        )
    }

    @Test
    fun `zero and full intensity both render without throwing`() {
        listOf(WeatherCondition.RAIN, WeatherCondition.STORM, WeatherCondition.SNOW).forEach { condition ->
            PosterRenderer.render(64, 64, 12f, PosterWeather(condition, 0))
            PosterRenderer.render(64, 64, 12f, PosterWeather(condition, 100))
        }
    }
}
