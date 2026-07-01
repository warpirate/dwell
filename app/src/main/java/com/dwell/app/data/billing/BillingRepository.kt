package com.dwell.app.data.billing

import android.app.Activity

interface BillingRepository {
    /** Product id of the one-time unlock, configured in Play Console. */
    val productId: String

    /** Launch the Play purchase flow for the unlock. Suspends until the flow resolves. */
    suspend fun launchPurchase(activity: Activity): PurchaseResult

    /**
     * Play's localized, formatted price for the unlock (e.g. "₹299.00"), or null if the
     * product/price can't be fetched. The paywall shows this so the price always matches
     * the Play Console configuration and the user's region.
     */
    suspend fun formattedPrice(): String?
}
