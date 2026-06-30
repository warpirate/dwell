package com.dwell.app.ui.wallpapers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Category
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.repository.PageCursor
import com.dwell.app.data.repository.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WallpapersViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WallpapersUiState())
    val uiState: StateFlow<WallpapersUiState> = _uiState.asStateFlow()

    // Pagination cursor for the current category. Reset on category change /
    // refresh. Kept here (not in UI state) because it is an opaque token.
    private var cursor: PageCursor? = null
    private var firstPageJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadCategories()
        loadFirstPage(showLoading = true)
        viewModelScope.launch {
            favoritesRepository.observeFavoriteWallpapers().collectLatest { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
        viewModelScope.launch {
            favoritesRepository.observeFavoriteIds().collectLatest { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    /** Toggle a wallpaper's saved state straight from the grid. Optimistic: the
     *  repo writes Room first, so [WallpapersUiState.favoriteIds] flips at once. */
    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { favoritesRepository.toggle(wallpaper) }
    }

    fun selectCategory(categoryId: String) {
        if (categoryId == _uiState.value.selectedCategoryId) return
        val favoritesMode = categoryId == Category.FAVORITES_ID
        _uiState.update {
            it.copy(selectedCategoryId = categoryId, isFavoritesMode = favoritesMode)
        }
        if (!favoritesMode) {
            loadFirstPage(showLoading = true)
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadCategories()
        loadFirstPage(showLoading = false)
    }

    fun retry() = loadFirstPage(showLoading = true)

    fun loadMore() {
        val state = _uiState.value
        if (state.contentState != ContentState.Content) return
        if (state.endReached || state.isLoadingMore || cursor == null) return

        // Remember which category this page belongs to. A fast category switch
        // mid-flight must not splice these results into the new category.
        val requestedCategory = repoCategoryId()
        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            repository.getWallpapers(requestedCategory, cursor)
                .onSuccess { page ->
                    if (requestedCategory != repoCategoryId()) return@onSuccess
                    cursor = page.cursor
                    _uiState.update {
                        it.copy(
                            wallpapers = it.wallpapers + page.wallpapers,
                            isLoadingMore = false,
                            endReached = page.endReached,
                            isOffline = page.fromCache,
                        )
                    }
                }
                .onFailure {
                    if (requestedCategory != repoCategoryId()) return@onFailure
                    // Non-fatal: keep what we have, stop paging this run.
                    _uiState.update { it.copy(isLoadingMore = false, endReached = true) }
                }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            repository.getCategories().onSuccess { categories ->
                _uiState.update {
                    it.copy(
                        categories = listOf(Category.all(), Category.favorites()) + categories,
                    )
                }
            }
            // On failure keep whatever chips we have; the grid can still load.
        }
    }

    private fun loadFirstPage(showLoading: Boolean) {
        // Cancel both the previous first-page AND any in-flight loadMore so a
        // stale page can't write into the new category's state.
        firstPageJob?.cancel()
        loadMoreJob?.cancel()
        cursor = null
        // Clean paging flags so the switched-to category starts fresh.
        _uiState.update {
            it.copy(
                isLoadingMore = false,
                endReached = false,
                contentState = if (showLoading) ContentState.Loading else it.contentState,
            )
        }
        firstPageJob = viewModelScope.launch {
            repository.getWallpapers(repoCategoryId(), cursor = null)
                .onSuccess { page ->
                    cursor = page.cursor
                    _uiState.update {
                        it.copy(
                            wallpapers = page.wallpapers,
                            contentState = if (page.wallpapers.isEmpty()) {
                                ContentState.Empty
                            } else {
                                ContentState.Content
                            },
                            endReached = page.endReached,
                            isOffline = page.fromCache,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            // Keep existing content on a refresh failure; only
                            // show the error screen when there's nothing to show.
                            contentState = if (it.wallpapers.isEmpty()) {
                                ContentState.Error
                            } else {
                                it.contentState
                            },
                            isRefreshing = false,
                        )
                    }
                }
        }
    }

    private fun repoCategoryId(): String? =
        _uiState.value.selectedCategoryId.takeIf { it != Category.ALL_ID }
}
