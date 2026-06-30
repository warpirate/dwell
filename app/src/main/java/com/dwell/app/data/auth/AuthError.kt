package com.dwell.app.data.auth

/** Domain auth failure, mapped from Firebase at the repository boundary. */
enum class AuthError { INVALID_CREDENTIALS, EMAIL_IN_USE, WEAK_PASSWORD, INVALID_EMAIL, NETWORK, UNKNOWN }
