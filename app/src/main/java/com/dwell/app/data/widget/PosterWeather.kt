package com.dwell.app.data.widget

/**
 * The 8 poster-scene weather conditions, each a bespoke compositor in WeatherPainter -
 * see design/widget-weather-fable.html for the visually-validated source every formula
 * was ported 1:1 from.
 */
enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY("Partly cloudy"),
    OVERCAST("Overcast"),
    FOG("Fog"),
    RAIN("Rain"),
    STORM("Thunderstorm"),
    SNOW("Snow"),
    HAZE("Haze"),
}

/**
 * A poster widget instance's chosen weather. Manually set (no live data source in this
 * slice). [intensity] is 0..100 and is ignored by the renderer when [condition] is CLEAR,
 * but still stored so flipping back to a precip condition remembers the last-chosen strength.
 */
data class PosterWeather(
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val intensity: Int = 70,
) {
    fun coerced(): PosterWeather = copy(intensity = intensity.coerceIn(0, 100))

    fun encode(): String = "${condition.name}|$intensity"

    companion object {
        val Default = PosterWeather()

        fun decode(raw: String): PosterWeather = runCatching {
            val parts = raw.split("|").also { require(it.size == 2) }
            PosterWeather(
                condition = WeatherCondition.valueOf(parts[0]),
                intensity = parts[1].toInt(),
            ).coerced()
        }.getOrDefault(Default)
    }
}
