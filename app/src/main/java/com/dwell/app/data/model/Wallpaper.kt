package com.dwell.app.data.model

/**
 * A wallpaper. Mirrors `wallpapers/{id}` (Backend Schema 1).
 *
 * `tags` is read for forward-compat (search is P2) but unused in v1 UI.
 * `createdAtMillis` is the Firestore timestamp flattened to epoch millis so the
 * model carries no Firestore types upward.
 */
data class Wallpaper(
    val id: String,
    val title: String?,
    val category: String,
    val thumbUrl: String,
    val fullPhoneUrl: String,
    val fullTabletUrl: String,
    val dominantColor: String,
    val isAiGenerated: Boolean,
    val order: Int,
    val createdAtMillis: Long,
) {
    /** Full-resolution URL for the device class. */
    fun fullUrl(isTablet: Boolean): String = if (isTablet) fullTabletUrl else fullPhoneUrl
}
