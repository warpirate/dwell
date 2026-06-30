package com.dwell.app.ui.preview

import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.wallpaper.WallpaperTarget

/** Progress of an apply action. Drives the button and the result toast. */
enum class ApplyState { Idle, Applying, Applied, Failed }

data class PreviewUiState(
    val wallpaper: Wallpaper? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val applyState: ApplyState = ApplyState.Idle,
    val isFavorite: Boolean = false,
)
