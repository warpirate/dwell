package com.dwell.app.data.favorites

import com.dwell.app.data.local.FavoriteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesMergeTest {

    private fun repo(
        dao: FakeFavoriteDao = FakeFavoriteDao(),
        wallpaperDao: FakeWallpaperDao = FakeWallpaperDao(),
        remote: FakeRemoteSource = FakeRemoteSource(),
        auth: FakeAuthRepository = FakeAuthRepository("u1"),
    ) = FavoritesRepositoryImpl(dao, wallpaperDao, remote, auth) { 1000L }

    @Test
    fun snapshotLocalFavorites_returnsRoomFavorites() = runTest {
        val dao = FakeFavoriteDao().apply {
            upsert(FavoriteEntity("a", 2L)); upsert(FavoriteEntity("b", 1L))
        }
        val sut = repo(dao = dao)

        val snap = sut.snapshotLocalFavorites()

        assertEquals(setOf("a", "b"), snap.map { it.wallpaperId }.toSet())
    }

    @Test
    fun mergeInto_pushesAllToRemote_withoutClearingRoom() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("b", 1L)) }
        val remote = FakeRemoteSource()
        val sut = repo(dao = dao, remote = remote)

        sut.mergeInto("existing", listOf(FavoriteRemote("b", 1L)))

        assertEquals(setOf("b"), remote.fetchAll("existing").map { it.wallpaperId }.toSet())
        assertTrue(dao.exists("b")) // Room not cleared
    }

    @Test
    fun collisionSequence_mergeBeforeReconcile_yieldsUnion() = runTest {
        // existing account already has "a" on the server; anon Room has "b"
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("b", 5L)) }
        val remote = FakeRemoteSource().apply { remote["existing"] = mutableMapOf("a" to 1L) }
        val auth = FakeAuthRepository("existing")
        val sut = repo(dao = dao, remote = remote, auth = auth)

        val snap = listOf(FavoriteRemote("b", 5L))
        sut.mergeInto("existing", snap)
        sut.reconcile()

        assertTrue(dao.exists("a"))
        assertTrue(dao.exists("b")) // union, "b" not lost
    }

    @Test
    fun clearLocal_emptiesRoom() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("x", 1L)) }
        val sut = repo(dao = dao)

        sut.clearLocal()

        assertFalse(dao.exists("x"))
    }

    @Test
    fun signOutSequence_clearThenReconcile_endsEmpty() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("old", 1L)) }
        val remote = FakeRemoteSource()
        val auth = FakeAuthRepository("newAnon")
        val sut = repo(dao = dao, remote = remote, auth = auth)

        sut.clearLocal()
        sut.reconcile()

        assertFalse(dao.exists("old"))
        assertEquals(0, dao.getAll().size)
    }
}
