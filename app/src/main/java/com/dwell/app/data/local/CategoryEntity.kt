package com.dwell.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dwell.app.data.model.Category

/**
 * Room mirror of categories so the chip row renders offline. The schema lists
 * only wallpaper/favorites caches; categories are tiny and the chips are useless
 * empty offline, so they are cached too. Flagged: extends the documented cache.
 */
@Entity(tableName = "category_cache")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val order: Int,
    val coverWallpaperId: String?,
)

fun CategoryEntity.toModel(): Category = Category(
    id = id,
    name = name,
    order = order,
    coverWallpaperId = coverWallpaperId,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    order = order,
    coverWallpaperId = coverWallpaperId,
)
