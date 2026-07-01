package com.dwell.app.ui.unlock

import android.app.Activity
import app.cash.turbine.test
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.PurchaseResult
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    private val premium = MutableStateFlow(false)
    private val entitlements = object : EntitlementRepository {
        override fun observePremium(): Flow<Boolean> = premium
    }
    private val billing = object : BillingRepository {
        override val productId = "unlock_premium"
        override suspend fun launchPurchase(activity: Activity): PurchaseResult =
            PurchaseResult.Verifying
        override suspend fun formattedPrice(): String? = "₹299.00"
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exposes premium from the repository`() = runTest {
        val vm = UnlockViewModel(entitlements, billing)
        vm.isPremium.test {
            assertEquals(false, awaitItem())
            premium.value = true
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
