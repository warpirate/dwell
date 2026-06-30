package com.dwell.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local copy of one favorite (Backend Schema 6, favorites_cache). The wallpaper
 * id is the key, matching the Firestore favorite doc id, so a toggle is a single
 * write with no query. addedAtMillis drives newest-saved-first ordering.
 */
@Entity(tableName = "favorites_cache")
data class FavoriteEntity(
    @PrimaryKey val wallpaperId: String,
    val addedAtMillis: Long,
)
