package com.dwell.app.data.favorites

import app.cash.turbine.test
import com.dwell.app.data.local.FavoriteEntity
import com.dwell.app.data.local.toModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesRepositoryImplTest {

    private fun repo(
        dao: FakeFavoriteDao = FakeFavoriteDao(),
        wallpaperDao: FakeWallpaperDao = FakeWallpaperDao(),
        remote: FakeRemoteSource = FakeRemoteSource(),
        auth: FakeAuthRepository = FakeAuthRepository("u1"),
        now: Long = 1000L,
    ) = FavoritesRepositoryImpl(dao, wallpaperDao, remote, auth) { now }

    @Test
    fun toggle_addsThenRemoves() = runTest {
        val dao = FakeFavoriteDao()
        val remote = FakeRemoteSource()
        val sut = repo(dao = dao, remote = remote)
        val w = wallpaperEntity("nature-1").toModel()

        sut.toggle(w)
        assertTrue(dao.exists("nature-1"))
        assertEquals(1, remote.fetchAll("u1").size)

        sut.toggle(w)
        assertFalse(dao.exists("nature-1"))
        assertEquals(0, remote.fetchAll("u1").size)
    }

    @Test
    fun toggle_keepsLocalStateWhenRemoteWriteFails() = runTest {
        val dao = FakeFavoriteDao()
        val remote = FakeRemoteSource().apply { failWrites = true }
        val sut = repo(dao = dao, remote = remote)

        sut.toggle(wallpaperEntity("nature-1").toModel())

        assertTrue(dao.exists("nature-1")) // optimistic local write survives
        assertEquals(0, remote.fetchAll("u1").size) // remote never got it
    }

    @Test
    fun reconcile_replacesLocalWithRemote() = runTest {
        val dao = FakeFavoriteDao()
        val remote = FakeRemoteSource().apply {
            remote["u1"] = mutableMapOf("a" to 1L, "b" to 2L)
        }
        val sut = repo(dao = dao, remote = remote)
        // seed a stale local favorite that is not on the server
        dao.upsert(FavoriteEntity("stale", 5L))

        sut.reconcile()

        assertFalse(dao.exists("stale"))
        assertTrue(dao.exists("a"))
        assertTrue(dao.exists("b"))
    }

    @Test
    fun reconcile_isNoOpWhenSignedOut() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("x", 1L)) }
        val sut = repo(dao = dao, auth = FakeAuthRepository(null))

        sut.reconcile()

        assertTrue(dao.exists("x")) // untouched
    }

    @Test
    fun observeFavoriteIds_emitsSavedIds() = runTest {
        val dao = FakeFavoriteDao()
        val sut = repo(dao = dao)

        sut.observeFavoriteIds().test {
            assertEquals(emptySet<String>(), awaitItem())
            sut.toggle(wallpaperEntity("nature-1").toModel())
            assertEquals(setOf("nature-1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeFavoriteWallpapers_skipsIdsMissingFromCache() = runTest {
        val dao = FakeFavoriteDao()
        val wallpaperDao = FakeWallpaperDao(listOf(wallpaperEntity("nature-1")))
        val sut = repo(dao = dao, wallpaperDao = wallpaperDao)

        // "ghost" is favorited but not in the wallpaper cache
        dao.upsert(FavoriteEntity("nature-1", 2L))
        dao.upsert(FavoriteEntity("ghost", 3L))

        sut.observeFavoriteWallpapers().test {
            val list = awaitItem()
            assertEquals(listOf("nature-1"), list.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
