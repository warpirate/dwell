package com.dwell.app.data.favorites

import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.UpgradeResult
import com.dwell.app.data.local.FavoriteDao
import com.dwell.app.data.local.FavoriteEntity
import com.dwell.app.data.local.WallpaperDao
import com.dwell.app.data.local.WallpaperEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeFavoriteDao : FavoriteDao {
    val state = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteEntity>> =
        state.asStateFlow().map { list -> list.sortedByDescending { it.addedAtMillis } }

    override suspend fun exists(id: String): Boolean =
        state.value.any { it.wallpaperId == id }

    override suspend fun upsert(favorite: FavoriteEntity) {
        state.value = state.value.filterNot { it.wallpaperId == favorite.wallpaperId } + favorite
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.wallpaperId == id }
    }

    override suspend fun getAll(): List<FavoriteEntity> =
        state.value.sortedByDescending { it.addedAtMillis }

    override suspend fun clear() { state.value = emptyList() }

    override suspend fun insertAll(favorites: List<FavoriteEntity>) {
        val ids = favorites.map { it.wallpaperId }.toSet()
        state.value = state.value.filterNot { it.wallpaperId in ids } + favorites
    }

    override suspend fun replaceAll(favorites: List<FavoriteEntity>) {
        clear(); insertAll(favorites)
    }
}

class FakeWallpaperDao(rows: List<WallpaperEntity> = emptyList()) : WallpaperDao {
    val rows = rows.associateBy { it.id }.toMutableMap()

    override suspend fun upsertAll(wallpapers: List<WallpaperEntity>) {
        wallpapers.forEach { rows[it.id] = it }
    }
    override suspend fun getAll(): List<WallpaperEntity> = rows.values.toList()
    override suspend fun getByCategory(categoryId: String): List<WallpaperEntity> =
        rows.values.filter { it.category == categoryId }
    override suspend fun getById(id: String): WallpaperEntity? = rows[id]
    override suspend fun getByIds(ids: List<String>): List<WallpaperEntity> =
        ids.mapNotNull { rows[it] }
}

class FakeRemoteSource : FavoritesRemoteSource {
    val remote = mutableMapOf<String, MutableMap<String, Long>>() // uid -> id -> addedAt
    var failWrites = false

    override suspend fun fetchAll(uid: String): List<FavoriteRemote> =
        remote[uid].orEmpty().map { (id, ts) -> FavoriteRemote(id, ts) }

    override suspend fun put(uid: String, wallpaperId: String, addedAtMillis: Long) {
        if (failWrites) error("offline")
        remote.getOrPut(uid) { mutableMapOf() }[wallpaperId] = addedAtMillis
    }

    override suspend fun remove(uid: String, wallpaperId: String) {
        if (failWrites) error("offline")
        remote[uid]?.remove(wallpaperId)
    }

    override suspend fun putAll(uid: String, favorites: List<FavoriteRemote>) {
        if (failWrites) error("offline")
        val m = remote.getOrPut(uid) { mutableMapOf() }
        favorites.forEach { m[it.wallpaperId] = it.addedAtMillis }
    }
}

class FakeAuthRepository(private val current: String?) : AuthRepository {
    override val uid: Flow<String?> = MutableStateFlow(current)
    override fun currentUid(): String? = current
    override suspend fun ensureSignedIn() {}
    override fun isAnonymous(): Boolean = current == null
    override fun currentEmail(): String? = null
    override suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult =
        UpgradeResult.Linked(current ?: "u1")
    override suspend fun linkGoogle(idToken: String): UpgradeResult =
        UpgradeResult.Linked(current ?: "u1")
    override suspend fun signOut() {}
}

fun wallpaperEntity(id: String, category: String = "nature") = WallpaperEntity(
    id = id, title = id, category = category, thumbUrl = "t", fullPhoneUrl = "p",
    fullTabletUrl = "tab", dominantColor = "#000000", isAiGenerated = true,
    order = 0, createdAtMillis = 0L,
)
