package com.dwell.app.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * Owns the Firebase Auth session. The app signs in anonymously on launch so
 * every install has a uid for favorites, with no login wall.
 */
interface AuthRepository {

    /** Current user id, null when signed out. Emits on auth state changes. */
    val uid: Flow<String?>

    /** Current user id right now, or null. */
    fun currentUid(): String?

    /** Sign in anonymously if there is no current user. Idempotent. */
    suspend fun ensureSignedIn()
}
