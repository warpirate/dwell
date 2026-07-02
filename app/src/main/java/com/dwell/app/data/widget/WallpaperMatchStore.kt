package com.dwell.app.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The current wallpaper's matched widget text colour — the moat's real data source.
 *
 * Written once, at wallpaper apply time, from the bitmap Dwell already owns. A generic widget
 * app can't reproduce this: Android 14+ blocks `WallpaperManager.getDrawable()` for normal apps,
 * so nobody else can read the user's wallpaper pixels. Null until the user applies a wallpaper
 * through Dwell.
 */
interface WallpaperMatchStore {
    /** The latest matched colour, or null if no Dwell wallpaper has been applied yet. */
    fun observe(): Flow<Int?>
    suspend fun get(): Int?
    suspend fun save(argb: Int)
}

@Singleton
class DataStoreWallpaperMatchStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WallpaperMatchStore {

    private val key = intPreferencesKey(KEY)

    override fun observe(): Flow<Int?> = dataStore.data.map { it[key] }

    override suspend fun get(): Int? = observe().first()

    override suspend fun save(argb: Int) {
        dataStore.edit { it[key] = argb }
    }

    private companion object {
        const val KEY = "wallpaper_matched_argb"
    }
}
