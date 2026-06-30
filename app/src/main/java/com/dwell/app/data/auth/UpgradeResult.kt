package com.dwell.app.data.auth

/** Outcome of an upgrade/sign-in attempt. */
sealed interface UpgradeResult {
    /** The anonymous user was linked; uid preserved, favorites already owned. */
    data class Linked(val uid: String) : UpgradeResult
    /** The credential belonged to an existing account; we adopted it (caller must merge). */
    data class SignedInExisting(val uid: String) : UpgradeResult
    data class Error(val error: AuthError) : UpgradeResult
}
