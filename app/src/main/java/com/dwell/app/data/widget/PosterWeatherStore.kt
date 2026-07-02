package com.dwell.app.data.widget

import kotlinx.coroutines.flow.Flow

/** Persists a [PosterWeather] per appWidgetId, for the poster Clock widget. */
interface PosterWeatherStore {
    fun observe(appWidgetId: Int): Flow<PosterWeather>
    suspend fun get(appWidgetId: Int): PosterWeather
    suspend fun save(appWidgetId: Int, weather: PosterWeather)
    suspend fun clear(appWidgetId: Int)
}
