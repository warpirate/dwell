package com.dwell.app.data.billing

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementRemoteSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : EntitlementRemoteSource {

    override fun observePremium(uid: String): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                // On error, snapshot is null: treat as not-premium rather than crash.
                trySend(snapshot?.getBoolean("premium") ?: false)
            }
        awaitClose { registration.remove() }
    }
}
