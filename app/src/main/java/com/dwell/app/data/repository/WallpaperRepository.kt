package com.dwell.app.data.repository

import com.dwell.app.data.model.Category
import com.dwell.app.data.model.Wallpaper
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Opaque pagination cursor. Wraps the last Firestore document of a page so the
 * caller can request the next page without knowing about Firestore types.
 */
class PageCursor internal constructor(internal val snapshot: DocumentSnapshot)

/** One page of wallpapers plus paging/offline metadata. */
data class WallpaperPage(
    val wallpapers: List<Wallpaper>,
    val cursor: PageCursor?,
    val endReached: Boolean,
    val fromCache: Boolean,
)

/**
 * The only thing that talks to Firestore + the Room cache for wallpapers
 * (Architecture rule: repositories own the remote-vs-cache decision).
 */
interface WallpaperRepository {

    /** Categories in chip order. Falls back to cache when offline. */
    suspend fun getCategories(): Result<List<Category>>

    /**
     * One page of wallpapers. [categoryId] null means the whole catalog
     * (newest first); a real id filters by category in server order.
     * [cursor] null requests the first page. Falls back to cache when offline.
     */
    suspend fun getWallpapers(
        categoryId: String?,
        cursor: PageCursor?,
        pageSize: Int = PAGE_SIZE,
    ): Result<WallpaperPage>

    /**
     * A single wallpaper by id, read from the local cache. Any wallpaper the
     * preview can open was already loaded into the grid, so it is cached. This
     * keeps the preview working offline and surviving process death.
     */
    suspend fun getWallpaper(id: String): Wallpaper?

    /** A cached wallpaper to show as a hero (e.g. the auth backdrop), or null. */
    suspend fun getHeroWallpaper(): Wallpaper?

    companion object {
        const val PAGE_SIZE = 20
    }
}
