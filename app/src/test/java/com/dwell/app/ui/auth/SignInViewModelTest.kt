package com.dwell.app.ui.auth

import com.dwell.app.data.auth.AuthError
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.UpgradeResult
import com.dwell.app.data.favorites.FavoriteRemote
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.repository.PageCursor
import com.dwell.app.data.repository.WallpaperPage
import com.dwell.app.data.repository.WallpaperRepository
import com.dwell.app.data.model.Category
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAuth(var result: UpgradeResult) : AuthRepository {
        override val uid: Flow<String?> = MutableStateFlow("u1")
        override fun currentUid(): String? = "u1"
        override suspend fun ensureSignedIn() {}
        override fun isAnonymous(): Boolean = true
        override fun currentEmail(): String? = null
        override suspend fun linkEmail(email: String, password: String, createAccount: Boolean) = result
        override suspend fun linkGoogle(idToken: String) = result
        override suspend fun signOut() {}
    }

    private class FakeWallpapers : WallpaperRepository {
        override suspend fun getCategories() = Result.success(emptyList<Category>())
        override suspend fun getWallpapers(categoryId: String?, cursor: PageCursor?, pageSize: Int) =
            Result.success(WallpaperPage(emptyList(), null, true, false))
        override suspend fun getWallpaper(id: String): Wallpaper? = null
        override suspend fun getHeroWallpaper(): Wallpaper? = null
    }

    private class RecordingFavorites(val snapshot: List<FavoriteRemote>) : FavoritesRepository {
        val calls = mutableListOf<String>()
        override fun observeFavoriteIds(): Flow<Set<String>> = MutableStateFlow(emptySet())
        override fun observeFavoriteWallpapers(): Flow<List<Wallpaper>> = MutableStateFlow(emptyList())
        override suspend fun toggle(wallpaper: Wallpaper) {}
        override suspend fun reconcile() { calls.add("reconcile") }
        override suspend fun snapshotLocalFavorites(): List<FavoriteRemote> { calls.add("snapshot"); return snapshot }
        override suspend fun mergeInto(uid: String, favorites: List<FavoriteRemote>) { calls.add("mergeInto:$uid:${favorites.size}") }
        override suspend fun clearLocal() { calls.add("clearLocal") }
    }

    @Test
    fun linked_reconcilesOnly_noMerge() = runTest {
        val fav = RecordingFavorites(listOf(FavoriteRemote("b", 1L)))
        val vm = SignInViewModel(FakeAuth(UpgradeResult.Linked("u1")), fav, FakeWallpapers())
        vm.onEmailChange("a@b.com"); vm.onPasswordChange("secret1"); vm.setMode(SignInMode.Create)

        vm.submitEmail()

        assertTrue(vm.uiState.value.done)
        assertEquals(listOf("snapshot", "reconcile"), fav.calls)
        assertTrue(fav.calls.none { it.startsWith("mergeInto") })
    }

    @Test
    fun signedInExisting_mergesThenReconciles_inOrder() = runTest {
        val fav = RecordingFavorites(listOf(FavoriteRemote("b", 1L)))
        val vm = SignInViewModel(FakeAuth(UpgradeResult.SignedInExisting("existing")), fav, FakeWallpapers())
        vm.onEmailChange("a@b.com"); vm.onPasswordChange("secret1"); vm.setMode(SignInMode.SignIn)

        vm.submitEmail()

        val snap = fav.calls.indexOf("snapshot")
        val merge = fav.calls.indexOf("mergeInto:existing:1")
        val recon = fav.calls.indexOf("reconcile")
        assertTrue(snap in 0 until merge)
        assertTrue(merge in 0 until recon)
        assertTrue(vm.uiState.value.done)
        assertTrue(vm.uiState.value.mergedExisting)
    }

    @Test
    fun error_setsInlineError_noDone() = runTest {
        val fav = RecordingFavorites(emptyList())
        val vm = SignInViewModel(FakeAuth(UpgradeResult.Error(AuthError.INVALID_CREDENTIALS)), fav, FakeWallpapers())
        vm.onEmailChange("a@b.com"); vm.onPasswordChange("x")

        vm.submitEmail()

        assertEquals(AuthError.INVALID_CREDENTIALS, vm.uiState.value.inlineError)
        assertEquals(false, vm.uiState.value.done)
        assertTrue(fav.calls.none { it == "reconcile" })
    }
}
