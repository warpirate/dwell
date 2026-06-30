package com.dwell.app.ui.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.repository.WallpaperRepository
import com.dwell.app.data.wallpaper.WallpaperApplier
import com.dwell.app.data.wallpaper.WallpaperTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WallpaperRepository,
    private val applier: WallpaperApplier,
) : ViewModel() {

    private val wallpaperId: String = checkNotNull(savedStateHandle[ARG_WALLPAPER_ID])

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val wallpaper = repository.getWallpaper(wallpaperId)
            _uiState.update {
                it.copy(
                    wallpaper = wallpaper,
                    isLoading = false,
                    notFound = wallpaper == null,
                )
            }
        }
    }

    fun selectTarget(target: WallpaperTarget) {
        _uiState.update { it.copy(target = target) }
    }

    /**
     * Applies the full-resolution image for the device class. [isTablet] picks
     * the resolution variant (Implementation Plan, Phase 1).
     */
    fun apply(isTablet: Boolean) {
        val state = _uiState.value
        val wallpaper = state.wallpaper ?: return
        if (state.applyState == ApplyState.Applying) return

        _uiState.update { it.copy(applyState = ApplyState.Applying) }
        viewModelScope.launch {
            val result = applier.apply(wallpaper.fullUrl(isTablet), state.target)
            _uiState.update {
                it.copy(
                    applyState = if (result.isSuccess) ApplyState.Applied else ApplyState.Failed,
                )
            }
        }
    }

    /** Acknowledge a finished apply so the toast fires once. */
    fun consumeApplyResult() {
        _uiState.update { it.copy(applyState = ApplyState.Idle) }
    }

    companion object {
        const val ARG_WALLPAPER_ID = "wallpaperId"
    }
}
