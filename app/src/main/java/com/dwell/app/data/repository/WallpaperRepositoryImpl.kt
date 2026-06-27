package com.dwell.app.data.repository

import com.dwell.app.data.local.CategoryDao
import com.dwell.app.data.local.WallpaperDao
import com.dwell.app.data.local.toEntity
import com.dwell.app.data.local.toModel
import com.dwell.app.data.model.Category
import com.dwell.app.data.model.Wallpaper
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val wallpaperDao: WallpaperDao,
    private val categoryDao: CategoryDao,
) : WallpaperRepository {

    override suspend fun getCategories(): Result<List<Category>> = runCatching {
        val snapshot = firestore.collection(COLLECTION_CATEGORIES)
            .orderBy(FIELD_ORDER, Query.Direction.ASCENDING)
            .get()
            .await()
        val categories = snapshot.documents.mapNotNull { it.toCategoryOrNull() }
        if (categories.isNotEmpty()) {
            categoryDao.upsertAll(categories.map { it.toEntity() })
        }
        categories
    }.recoverCatching { error ->
        // Only fall back to cache when genuinely offline. Real errors (e.g. a
        // missing index, permission denial) must surface so they get fixed.
        if (error.isOffline()) {
            categoryDao.getAll().map { it.toModel() }
        } else {
            throw error
        }
    }

    override suspend fun getWallpapers(
        categoryId: String?,
        cursor: PageCursor?,
        pageSize: Int,
    ): Result<WallpaperPage> = runCatching {
        val snapshot = buildWallpaperQuery(categoryId, cursor, pageSize).get().await()
        val wallpapers = snapshot.documents.mapNotNull { it.toWallpaperOrNull() }
        if (wallpapers.isNotEmpty()) {
            wallpaperDao.upsertAll(wallpapers.map { it.toEntity() })
        }
        WallpaperPage(
            wallpapers = wallpapers,
            cursor = snapshot.documents.lastOrNull()?.let { PageCursor(it) },
            endReached = snapshot.documents.size < pageSize,
            fromCache = snapshot.metadata.isFromCache,
        )
    }.recoverCatching { error ->
        if (error.isOffline() && cursor == null) {
            // First page offline with no Firestore cache: serve Room.
            val cached = if (categoryId == null) {
                wallpaperDao.getAll()
            } else {
                wallpaperDao.getByCategory(categoryId)
            }
            WallpaperPage(
                wallpapers = cached.map { it.toModel() },
                cursor = null,
                endReached = true,
                fromCache = true,
            )
        } else if (error.isOffline()) {
            // Can't page further offline; stop cleanly.
            WallpaperPage(emptyList(), cursor = null, endReached = true, fromCache = true)
        } else {
            throw error
        }
    }

    private fun buildWallpaperQuery(
        categoryId: String?,
        cursor: PageCursor?,
        pageSize: Int,
    ): Query {
        val base = firestore.collection(COLLECTION_WALLPAPERS)
        val ordered = if (categoryId == null) {
            // Whole catalog, newest first.
            base.orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
        } else {
            // One category, in manual server order. Needs a composite index
            // (category ASC, order ASC). See firestore.indexes.json.
            base.whereEqualTo(FIELD_CATEGORY, categoryId)
                .orderBy(FIELD_ORDER, Query.Direction.ASCENDING)
        }
        val limited = ordered.limit(pageSize.toLong())
        return cursor?.let { limited.startAfter(it.snapshot) } ?: limited
    }

    private fun DocumentSnapshot.toWallpaperOrNull(): Wallpaper? {
        val thumb = getString("urls.thumb") ?: return null
        val fullPhone = getString("urls.full_phone") ?: thumb
        val fullTablet = getString("urls.full_tablet") ?: fullPhone
        val category = getString(FIELD_CATEGORY) ?: return null
        return Wallpaper(
            id = id,
            title = getString("title"),
            category = category,
            thumbUrl = thumb,
            fullPhoneUrl = fullPhone,
            fullTabletUrl = fullTablet,
            dominantColor = getString("dominantColor") ?: DEFAULT_DOMINANT_COLOR,
            isAiGenerated = getBoolean("isAiGenerated") ?: true,
            order = getLong(FIELD_ORDER)?.toInt() ?: 0,
            createdAtMillis = getTimestamp(FIELD_CREATED_AT)?.toDate()?.time ?: 0L,
        )
    }

    private fun DocumentSnapshot.toCategoryOrNull(): Category? {
        val name = getString("name") ?: return null
        return Category(
            id = id,
            name = name,
            order = getLong(FIELD_ORDER)?.toInt() ?: 0,
            coverWallpaperId = getString("coverWallpaperId"),
        )
    }

    /** True only for transient connectivity failures, not logic/config errors. */
    private fun Throwable.isOffline(): Boolean =
        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.UNAVAILABLE

    private companion object {
        const val COLLECTION_WALLPAPERS = "wallpapers"
        const val COLLECTION_CATEGORIES = "categories"
        const val FIELD_CATEGORY = "category"
        const val FIELD_ORDER = "order"
        const val FIELD_CREATED_AT = "createdAt"
        const val DEFAULT_DOMINANT_COLOR = "#1A1A18"
    }
}
