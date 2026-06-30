package com.dwell.app.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    override fun isAnonymous(): Boolean = auth.currentUser?.isAnonymous ?: true

    override fun currentEmail(): String? = auth.currentUser?.email?.takeIf { it.isNotBlank() }

    override suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult {
        val cred = EmailAuthProvider.getCredential(email, password)
        return try {
            val user = auth.currentUser
            if (createAccount && user != null && user.isAnonymous) {
                user.linkWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.Linked(user.uid)
            } else {
                auth.signInWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.SignedInExisting(auth.currentUser!!.uid)
            }
        } catch (e: Throwable) {
            UpgradeResult.Error(e.toAuthError())
        }
    }

    override suspend fun linkGoogle(idToken: String): UpgradeResult {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val user = auth.currentUser
            if (user != null && user.isAnonymous) {
                user.linkWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.Linked(user.uid)
            } else {
                auth.signInWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.SignedInExisting(auth.currentUser!!.uid)
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            // Google credential already on another account: adopt it.
            try {
                auth.signInWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.SignedInExisting(auth.currentUser!!.uid)
            } catch (e2: Throwable) {
                UpgradeResult.Error(e2.toAuthError())
            }
        } catch (e: Throwable) {
            UpgradeResult.Error(e.toAuthError())
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        auth.signInAnonymously().await()
    }

    private suspend fun ensureUserDoc() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        val ref = firestore.collection("users").document(user.uid)
        val providerId = user.providerData
            .firstOrNull { it.providerId != "firebase" }?.providerId
        val data = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "provider" to when (providerId) {
                GoogleAuthProvider.PROVIDER_ID -> "google"
                else -> "password"
            },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!ref.get().await().exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }
        ref.set(data, SetOptions.merge()).await()
    }

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL
            else AuthError.INVALID_CREDENTIALS
        is FirebaseNetworkException -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }
}
