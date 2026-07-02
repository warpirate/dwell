package com.dwell.app.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStorePosterWeatherStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PosterWeatherStore {

    private fun key(id: Int) = stringPreferencesKey("poster_weather_$id")

    override fun observe(appWidgetId: Int): Flow<PosterWeather> =
        dataStore.data.map { prefs ->
            prefs[key(appWidgetId)]?.let { PosterWeather.decode(it) } ?: PosterWeather.Default
        }

    override suspend fun get(appWidgetId: Int): PosterWeather = observe(appWidgetId).first()

    override suspend fun save(appWidgetId: Int, weather: PosterWeather) {
        dataStore.edit { it[key(appWidgetId)] = weather.coerced().encode() }
    }

    override suspend fun clear(appWidgetId: Int) {
        dataStore.edit { it.remove(key(appWidgetId)) }
    }
}
