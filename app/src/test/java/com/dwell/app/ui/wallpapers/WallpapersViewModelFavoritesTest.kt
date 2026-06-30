package com.dwell.app.ui.wallpapers

import app.cash.turbine.test
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Category
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.repository.PageCursor
import com.dwell.app.data.repository.WallpaperPage
import com.dwell.app.data.repository.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersViewModelFavoritesTest {

    // Unconfined so the view model's init collectors run eagerly and the state
    // is settled by the time the test reads it.
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun wallpaper(id: String) = Wallpaper(
        id = id, title = id, category = "nature", thumbUrl = "t", fullPhoneUrl = "p",
        fullTabletUrl = "tab", dominantColor = "#000000", isAiGenerated = true,
        order = 0, createdAtMillis = 0L,
    )

    private val emptyPage = WallpaperPage(emptyList(), cursor = null, endReached = true, fromCache = false)

    private class FakeWallpaperRepo(private val page: WallpaperPage) : WallpaperRepository {
        override suspend fun getCategories() = Result.success(emptyList<Category>())
        override suspend fun getWallpapers(categoryId: String?, cursor: PageCursor?, pageSize: Int) =
            Result.success(page)
        override suspend fun getWallpaper(id: String): Wallpaper? = null
    }

    private class FakeFavoritesRepo(val favs: List<Wallpaper>) : FavoritesRepository {
        val ids = MutableStateFlow(favs.map { it.id }.toSet())
        override fun observeFavoriteIds(): Flow<Set<String>> = ids
        override fun observeFavoriteWallpapers(): Flow<List<Wallpaper>> = MutableStateFlow(favs)
        override suspend fun toggle(wallpaper: Wallpaper) {}
        override suspend fun reconcile() {}
        override suspend fun snapshotLocalFavorites() = emptyList<com.dwell.app.data.favorites.FavoriteRemote>()
        override suspend fun mergeInto(uid: String, favorites: List<com.dwell.app.data.favorites.FavoriteRemote>) {}
        override suspend fun clearLocal() {}
    }

    @Test
    fun selectingSavedChip_entersFavoritesMode() = runTest {
        val vm = WallpapersViewModel(
            FakeWallpaperRepo(emptyPage),
            FakeFavoritesRepo(listOf(wallpaper("nature-1"))),
        )

        vm.selectCategory(Category.FAVORITES_ID)

        vm.uiState.test {
            // Unconfined dispatcher: state already settled. Latest item holds.
            val state = awaitItem()
            assertTrue(state.isFavoritesMode)
            assertEquals(listOf("nature-1"), state.favorites.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingRealCategory_leavesFavoritesMode() = runTest {
        val vm = WallpapersViewModel(
            FakeWallpaperRepo(emptyPage),
            FakeFavoritesRepo(emptyList()),
        )
        vm.selectCategory(Category.FAVORITES_ID)
        vm.selectCategory(Category.ALL_ID)

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isFavoritesMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
