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

    /**
     * Create-account mode links email/password to the anonymous user (uid kept).
     * Sign-in mode signs in to an existing account (caller merges favorites).
     */
    suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult

    /** Link/sign-in with a Google ID token. */
    suspend fun linkGoogle(idToken: String): UpgradeResult

    /** Whether the current user is anonymous (no real account). */
    fun isAnonymous(): Boolean

    /** Email of the current user, or null when anonymous. */
    fun currentEmail(): String?

    /** Sign out, then immediately re-create an anonymous session (never null uid). */
    suspend fun signOut()
}
