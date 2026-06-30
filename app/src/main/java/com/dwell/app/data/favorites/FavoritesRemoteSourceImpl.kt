package com.dwell.app.data.favorites

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRemoteSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FavoritesRemoteSource {

    override suspend fun fetchAll(uid: String): List<FavoriteRemote> {
        val snapshot = collection(uid).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("wallpaperId") ?: doc.id
            val addedAt = doc.getTimestamp("addedAt")?.toDate()?.time ?: 0L
            FavoriteRemote(id, addedAt)
        }
    }

    override suspend fun put(uid: String, wallpaperId: String, addedAtMillis: Long) {
        collection(uid).document(wallpaperId).set(
            mapOf(
                "wallpaperId" to wallpaperId,
                "addedAt" to Timestamp(Date(addedAtMillis)),
            ),
        ).await()
    }

    override suspend fun remove(uid: String, wallpaperId: String) {
        collection(uid).document(wallpaperId).delete().await()
    }

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("favorites")
}
