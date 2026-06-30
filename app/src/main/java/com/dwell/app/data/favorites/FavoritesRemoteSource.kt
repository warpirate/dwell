package com.dwell.app.data.favorites

/** Firestore access for the favorites subcollection. Behind a seam so the
 *  repository is testable with a fake. */
interface FavoritesRemoteSource {
    suspend fun fetchAll(uid: String): List<FavoriteRemote>
    suspend fun put(uid: String, wallpaperId: String, addedAtMillis: Long)
    suspend fun remove(uid: String, wallpaperId: String)
}
