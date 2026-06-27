package com.dwell.app.ui.wallpapers

import com.dwell.app.data.model.Category
import com.dwell.app.data.model.Wallpaper

/** Coarse state of the main grid load. Offline is a separate overlay flag. */
enum class ContentState { Loading, Content, Empty, Error }

data class WallpapersUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = Category.ALL_ID,
    val wallpapers: List<Wallpaper> = emptyList(),
    val contentState: ContentState = ContentState.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val isOffline: Boolean = false,
)
