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
class DataStoreWidgetStyleStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WidgetStyleStore {

    private fun key(id: Int) = stringPreferencesKey("widget_style_$id")

    override fun observe(appWidgetId: Int): Flow<WidgetStyle> =
        dataStore.data.map { prefs ->
            prefs[key(appWidgetId)]?.let { WidgetStyle.decode(it) } ?: WidgetStyle.Default
        }

    override suspend fun get(appWidgetId: Int): WidgetStyle = observe(appWidgetId).first()

    override suspend fun save(appWidgetId: Int, style: WidgetStyle) {
        dataStore.edit { it[key(appWidgetId)] = style.coerced().encode() }
    }

    override suspend fun clear(appWidgetId: Int) {
        dataStore.edit { it.remove(key(appWidgetId)) }
    }
}
