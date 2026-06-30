package com.dwell.app.data.favorites

import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.local.FavoriteDao
import com.dwell.app.data.local.FavoriteEntity
import com.dwell.app.data.local.WallpaperDao
import com.dwell.app.data.local.toModel
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.util.NowProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val wallpaperDao: WallpaperDao,
    private val remote: FavoritesRemoteSource,
    private val auth: AuthRepository,
    private val now: NowProvider,
) : FavoritesRepository {

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeAll().map { list -> list.map { it.wallpaperId }.toSet() }

    override fun observeFavoriteWallpapers(): Flow<List<Wallpaper>> =
        favoriteDao.observeAll().map { favorites ->
            val rows = wallpaperDao.getByIds(favorites.map { it.wallpaperId })
                .associateBy { it.id }
            // Preserve favorite order (newest-saved first); drop uncached rows.
            favorites.mapNotNull { rows[it.wallpaperId]?.toModel() }
        }

    override suspend fun toggle(wallpaper: Wallpaper) {
        val id = wallpaper.id
        val uid = auth.currentUid()
        if (favoriteDao.exists(id)) {
            favoriteDao.deleteById(id)
            if (uid != null) runCatching { remote.remove(uid, id) }
        } else {
            val ts = now.nowMillis()
            favoriteDao.upsert(FavoriteEntity(id, ts))
            if (uid != null) runCatching { remote.put(uid, id, ts) }
        }
    }

    override suspend fun reconcile() {
        val uid = auth.currentUid() ?: return
        runCatching {
            val entities = remote.fetchAll(uid).map { FavoriteEntity(it.wallpaperId, it.addedAtMillis) }
            favoriteDao.replaceAll(entities)
        }
    }
}
