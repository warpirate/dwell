package com.dwell.app.data.billing

import android.app.Activity

interface BillingRepository {
    /** Product id of the one-time unlock, configured in Play Console. */
    val productId: String

    /** Launch the Play purchase flow for the unlock. Suspends until the flow resolves. */
    suspend fun launchPurchase(activity: Activity): PurchaseResult
}
