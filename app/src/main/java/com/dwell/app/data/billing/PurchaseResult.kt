package com.dwell.app.data.billing

/** Outcome of a purchase attempt. The entitlement itself arrives via the Firestore listener. */
sealed interface PurchaseResult {
    /** Purchase succeeded and was sent for server verification. */
    data object Verifying : PurchaseResult
    /** User dismissed the Play sheet. */
    data object Cancelled : PurchaseResult
    /** User already owns the unlock; verification was re-triggered. */
    data object AlreadyOwned : PurchaseResult
    /** Anything else (billing unavailable, network, verify call failed). */
    data class Error(val message: String) : PurchaseResult
}
