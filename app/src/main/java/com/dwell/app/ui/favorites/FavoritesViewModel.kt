package com.dwell.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Wallpaper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: FavoritesRepository,
) : ViewModel() {

    val favorites: StateFlow<List<Wallpaper>> =
        repository.observeFavoriteWallpapers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Un-save (or re-save) from the favorites grid. Optimistic local write. */
    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { repository.toggle(wallpaper) }
    }
}
