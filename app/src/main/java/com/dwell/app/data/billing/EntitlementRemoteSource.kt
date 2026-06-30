package com.dwell.app.data.billing

import kotlinx.coroutines.flow.Flow

/** Reads the server-owned `premium` entitlement flag for a user. */
interface EntitlementRemoteSource {

    /**
     * Emits the user's `premium` flag and re-emits on every server change.
     * Missing doc or missing field → false. Served from Firestore's offline
     * cache when offline.
     */
    fun observePremium(uid: String): Flow<Boolean>
}
