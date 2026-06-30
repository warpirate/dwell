package com.dwell.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.dwell.app.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Drives the system Google chooser and returns the ID token, or null if the user
 * dismissed it / no Google account is available. Must be called with an Activity
 * context (the system sheet needs one). Only the idToken crosses into the
 * repository, so the auth layer stays Android-UI-free.
 */
suspend fun getGoogleIdToken(context: Context): String? {
    val option = GetSignInWithGoogleOption.Builder(
        context.getString(R.string.default_web_client_id),
    ).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val response = CredentialManager.create(context).getCredential(context, request)
        val cred = response.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(cred.data).idToken
        } else {
            null
        }
    } catch (e: Exception) {
        null // NoCredentialException / user cancelled / no provider
    }
}
