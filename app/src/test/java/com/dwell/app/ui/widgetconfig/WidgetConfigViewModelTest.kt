package com.dwell.app.ui.widgetconfig

import app.cash.turbine.test
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetPreset
import com.dwell.app.data.widget.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        store: FakeWidgetStyleStore = FakeWidgetStyleStore(),
        matchStore: FakeWallpaperMatchStore = FakeWallpaperMatchStore(),
        billing: FakeBilling = FakeBilling(),
        entitlements: FakeEntitlements = FakeEntitlements(premium = true),
    ) = WidgetConfigViewModel(store, matchStore, billing, entitlements)

    @Test
    fun `premium flows through`() = runTest {
        vm(entitlements = FakeEntitlements(premium = true)).isPremium.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setColor updates the draft`() = runTest {
        val vm = vm()
        vm.setColor(WidgetColor.GREEN)
        assertEquals(WidgetColor.GREEN, vm.draft.value.color)
    }

    @Test
    fun `save persists the draft`() = runTest {
        val store = FakeWidgetStyleStore()
        val vm = vm(store = store)
        vm.setColor(WidgetColor.GREEN)
        vm.save(appWidgetId = 7)
        assertEquals(WidgetStyle(color = WidgetColor.GREEN), store.get(7))
    }

    @Test
    fun `load reads existing style into the draft`() = runTest {
        val store = FakeWidgetStyleStore()
        store.save(7, WidgetStyle(color = WidgetColor.SAND))
        val vm = vm(store = store, entitlements = FakeEntitlements(premium = false))
        vm.load(7)
        assertEquals(WidgetColor.SAND, vm.draft.value.color)
    }

    @Test
    fun `selecting the free green preset needs no unlock`() = runTest {
        val vm = vm(entitlements = FakeEntitlements(premium = false))
        vm.selectPreset(WidgetPreset.SAGE)
        assertEquals(WidgetColor.GREEN, vm.draft.value.color)
        vm.needsUnlock.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a premium preset while free needs unlock`() = runTest {
        val vm = vm(entitlements = FakeEntitlements(premium = false))
        vm.selectPreset(WidgetPreset.GOLD)
        vm.needsUnlock.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a premium user never needs to unlock a premium preset`() = runTest {
        val vm = vm(entitlements = FakeEntitlements(premium = true))
        vm.selectPreset(WidgetPreset.GOLD)
        vm.needsUnlock.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `price label comes from billing`() = runTest {
        val vm = vm(billing = FakeBilling(price = "₹249.00"), entitlements = FakeEntitlements(premium = false))
        vm.priceLabel.test {
            assertEquals("₹249.00", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no matched colour until a wallpaper has been applied`() = runTest {
        vm().wallpaperMatch.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `matchCurrentWallpaper applies the persisted colour to the draft`() = runTest {
        val matched = 0xFFD9A38C.toInt()
        val vm = vm(matchStore = FakeWallpaperMatchStore(matched = matched))
        vm.matchCurrentWallpaper()
        assertEquals(matched, vm.draft.value.matchedArgb)
    }

    @Test
    fun `matchCurrentWallpaper is a no-op when no wallpaper has been applied`() = runTest {
        val vm = vm()
        vm.matchCurrentWallpaper()
        assertNull(vm.draft.value.matchedArgb)
    }
}
