package com.dwell.app.data.model

/**
 * A wallpaper category (chip). Mirrors `categories/{id}` (Backend Schema 1).
 * [coverWallpaperId] is unused in v1 UI but read for forward-compat.
 */
data class Category(
    val id: String,
    val name: String,
    val order: Int,
    val coverWallpaperId: String? = null,
) {
    companion object {
        /**
         * Synthetic "All" chip. Not a Firestore doc; selecting it queries every
         * wallpaper newest-first so the landing shows the whole catalog and a
         * chip is always selected (accent needs a selected target). Real chips
         * filter by [Category.id]. Flagged decision, not from the schema.
         */
        const val ALL_ID = "__all__"

        fun all(name: String = "All"): Category =
            Category(id = ALL_ID, name = name, order = -1)
    }
}
