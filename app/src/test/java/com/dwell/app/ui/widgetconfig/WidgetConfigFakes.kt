package com.dwell.app.ui.widgetconfig

import android.app.Activity
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.PurchaseResult
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.data.widget.WidgetStyleStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWidgetStyleStore : WidgetStyleStore {
    val styles = MutableStateFlow<Map<Int, WidgetStyle>>(emptyMap())
    override fun observe(appWidgetId: Int): Flow<WidgetStyle> =
        styles.map { it[appWidgetId] ?: WidgetStyle.Default }
    override suspend fun get(appWidgetId: Int): WidgetStyle = styles.value[appWidgetId] ?: WidgetStyle.Default
    override suspend fun save(appWidgetId: Int, style: WidgetStyle) {
        styles.value = styles.value + (appWidgetId to style)
    }
    override suspend fun clear(appWidgetId: Int) {
        styles.value = styles.value - appWidgetId
    }
}

class FakeEntitlements(premium: Boolean) : EntitlementRepository {
    private val flow = MutableStateFlow(premium)
    override fun observePremium(): Flow<Boolean> = flow
}

class FakeBilling : BillingRepository {
    override val productId = "unlock_premium"
    override suspend fun launchPurchase(activity: Activity): PurchaseResult = PurchaseResult.Verifying
}
