package com.dwell.app.ui.widgetconfig

import app.cash.turbine.test
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `premium flows through`() = runTest {
        val vm = WidgetConfigViewModel(FakeWidgetStyleStore(), FakeBilling(), FakeEntitlements(premium = true))
        vm.isPremium.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setColor updates the draft`() = runTest {
        val vm = WidgetConfigViewModel(FakeWidgetStyleStore(), FakeBilling(), FakeEntitlements(premium = true))
        vm.setColor(WidgetColor.GREEN)
        assertEquals(WidgetColor.GREEN, vm.draft.value.color)
    }

    @Test
    fun `save persists the draft`() = runTest {
        val store = FakeWidgetStyleStore()
        val vm = WidgetConfigViewModel(store, FakeBilling(), FakeEntitlements(premium = true))
        vm.setColor(WidgetColor.GREEN)
        vm.save(appWidgetId = 7)
        assertEquals(WidgetStyle(color = WidgetColor.GREEN), store.get(7))
    }

    @Test
    fun `load reads existing style into the draft`() = runTest {
        val store = FakeWidgetStyleStore()
        store.save(7, WidgetStyle(color = WidgetColor.SAND))
        val vm = WidgetConfigViewModel(store, FakeBilling(), FakeEntitlements(premium = false))
        vm.load(7)
        assertEquals(WidgetColor.SAND, vm.draft.value.color)
    }
}
