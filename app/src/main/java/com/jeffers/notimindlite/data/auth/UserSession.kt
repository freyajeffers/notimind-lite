package com.jeffers.notimindlite.data.auth

data class UserSession(
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val error: String? = null
)
