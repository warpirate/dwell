package com.dwell.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-device cache. exportSchema is off for Phase 1; once the schema stabilizes
 * and real migrations are needed, turn it on and add a schema dir + migrations.
 */
@Database(
    entities = [WallpaperEntity::class, CategoryEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class DwellDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun categoryDao(): CategoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val NAME = "dwell.db"
    }
}
