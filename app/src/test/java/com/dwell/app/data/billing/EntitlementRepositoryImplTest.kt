package com.dwell.app.data.billing

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementRepositoryImplTest {

    private fun repo(auth: FakeMutableAuthRepository, remote: FakeEntitlementRemoteSource) =
        EntitlementRepositoryImpl(auth, remote)

    @Test
    fun `signed out emits false`() = runTest {
        val repo = repo(FakeMutableAuthRepository(null), FakeEntitlementRemoteSource())
        repo.observePremium().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `user without premium emits false`() = runTest {
        val repo = repo(FakeMutableAuthRepository("u1"), FakeEntitlementRemoteSource())
        repo.observePremium().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `premium flag true emits true`() = runTest {
        val remote = FakeEntitlementRemoteSource().apply { setPremium("u1", true) }
        val repo = repo(FakeMutableAuthRepository("u1"), remote)
        repo.observePremium().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flips when the server flag changes`() = runTest {
        val remote = FakeEntitlementRemoteSource()
        val repo = repo(FakeMutableAuthRepository("u1"), remote)
        repo.observePremium().test {
            assertEquals(false, awaitItem())
            remote.setPremium("u1", true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-evaluates when the uid changes`() = runTest {
        val remote = FakeEntitlementRemoteSource().apply { setPremium("paid", true) }
        val auth = FakeMutableAuthRepository(null)
        val repo = repo(auth, remote)
        repo.observePremium().test {
            assertEquals(false, awaitItem()) // signed out
            auth.setUid("paid")
            assertEquals(true, awaitItem())  // switched to a premium account
            cancelAndIgnoreRemainingEvents()
        }
    }
}
