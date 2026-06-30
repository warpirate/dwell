package com.dwell.app.data.favorites

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

    override suspend fun putAll(uid: String, favorites: List<FavoriteRemote>) {
        if (favorites.isEmpty()) return
        // Chunk under the 500-write batch limit.
        favorites.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { fav ->
                val ref = collection(uid).document(fav.wallpaperId)
                batch.set(
                    ref,
                    mapOf(
                        "wallpaperId" to fav.wallpaperId,
                        "addedAt" to Timestamp(Date(fav.addedAtMillis)),
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
    }

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("favorites")
}
