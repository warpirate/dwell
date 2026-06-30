package com.dwell.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WallpaperDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(wallpapers: List<WallpaperEntity>)

    /** All cached wallpapers, newest first. Used for the offline "All" view. */
    @Query("SELECT * FROM wallpaper_cache ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<WallpaperEntity>

    /** Cached wallpapers for one category, in server order. Offline filter. */
    @Query("SELECT * FROM wallpaper_cache WHERE category = :categoryId ORDER BY `order` ASC")
    suspend fun getByCategory(categoryId: String): List<WallpaperEntity>

    @Query("SELECT * FROM wallpaper_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WallpaperEntity?

    /** Cached wallpapers for a set of ids, any order. Used to render favorites. */
    @Query("SELECT * FROM wallpaper_cache WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WallpaperEntity>
}
