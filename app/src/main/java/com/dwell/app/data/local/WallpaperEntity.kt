package com.dwell.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dwell.app.data.model.Wallpaper

/**
 * Room mirror of a wallpaper so the grid renders offline (Backend Schema 6).
 * The schema lists the grid-minimum fields; this also stores the full-res URLs
 * and order/createdAt so the preview + apply path works offline and the cached
 * list keeps server ordering. Flagged: superset of the documented minimum.
 */
@Entity(tableName = "wallpaper_cache")
data class WallpaperEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val category: String,
    val thumbUrl: String,
    val fullPhoneUrl: String,
    val fullTabletUrl: String,
    val dominantColor: String,
    val isAiGenerated: Boolean,
    val order: Int,
    val createdAtMillis: Long,
)

fun WallpaperEntity.toModel(): Wallpaper = Wallpaper(
    id = id,
    title = title,
    category = category,
    thumbUrl = thumbUrl,
    fullPhoneUrl = fullPhoneUrl,
    fullTabletUrl = fullTabletUrl,
    dominantColor = dominantColor,
    isAiGenerated = isAiGenerated,
    order = order,
    createdAtMillis = createdAtMillis,
)

fun Wallpaper.toEntity(): WallpaperEntity = WallpaperEntity(
    id = id,
    title = title,
    category = category,
    thumbUrl = thumbUrl,
    fullPhoneUrl = fullPhoneUrl,
    fullTabletUrl = fullTabletUrl,
    dominantColor = dominantColor,
    isAiGenerated = isAiGenerated,
    order = order,
    createdAtMillis = createdAtMillis,
)
