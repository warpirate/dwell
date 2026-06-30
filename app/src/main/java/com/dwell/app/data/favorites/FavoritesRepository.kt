package com.dwell.app.data.favorites

import com.dwell.app.data.model.Wallpaper
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {

    /** Ids of saved wallpapers. Drives heart fill state. */
    fun observeFavoriteIds(): Flow<Set<String>>

    /** Saved wallpapers, newest-saved first, joined to the wallpaper cache. */
    fun observeFavoriteWallpapers(): Flow<List<Wallpaper>>

    /** Add if absent, remove if present. Optimistic local write, then remote. */
    suspend fun toggle(wallpaper: Wallpaper)

    /** Replace the local cache from the remote set. No-op when signed out or offline. */
    suspend fun reconcile()
}
