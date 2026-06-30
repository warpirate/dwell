package com.dwell.app.ui.auth

import com.dwell.app.data.auth.AuthError

enum class SignInMode { SignIn, Create }

data class SignInUiState(
    val mode: SignInMode = SignInMode.SignIn,
    val email: String = "",
    val password: String = "",
    val inProgress: Boolean = false,
    val inlineError: AuthError? = null,
    val done: Boolean = false,        // success -> dismiss the sheet
    val mergedExisting: Boolean = false, // show the "kept your favorites" toast
)
