package com.dwell.app.data.billing

import kotlinx.coroutines.flow.Flow

/**
 * The premium entitlement gate. `true` = ads removed + coordinated layer
 * unlocked. Read this anywhere a feature must be gated; never read the raw
 * Firestore field elsewhere.
 */
interface EntitlementRepository {

    /** Reactive entitlement. Re-emits on sign-in/out and on server changes. */
    fun observePremium(): Flow<Boolean>
}
