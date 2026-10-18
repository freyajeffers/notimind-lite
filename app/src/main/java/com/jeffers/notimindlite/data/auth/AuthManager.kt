package com.jeffers.notimindlite.data.auth

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val supabaseAuth = SupabaseAuthManager(context)
    private val _session = MutableStateFlow(
        supabaseAuth.currentSession().takeIf { it.isAuthenticated } ?: mapFirebaseUser(firebaseAuth.currentUser)
    )
    val session: StateFlow<UserSession> = _session.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val supabaseSession = supabaseAuth.currentSession()
            _session.value = if (supabaseSession.isAuthenticated) supabaseSession else mapFirebaseUser(auth.currentUser)
        }
    }

    suspend fun signInWithGoogle(): Result<UserSession> {
        _session.value = _session.value.copy(isAuthenticating = true, error = null)
        supabaseAuth.beginGoogleSignIn()
        return Result.success(_session.value)
    }

    suspend fun handleIntent(intent: Intent): Result<UserSession>? {
        val callbackUri = intent.data?.takeIf { uri ->
            uri.scheme == "notimindlite" && uri.host == "supabase-callback"
        }
        return callbackUri?.let { uri ->
            supabaseAuth.completeCallback(uri).also { result ->
                _session.value = result.getOrElse {
                    _session.value.copy(
                        isAuthenticating = false,
                        error = it.localizedMessage ?: "Sign-In failed"
                    )
                }
            }
        }
    }

    fun signOut() {
        supabaseAuth.signOut()
        firebaseAuth.signOut()
        _session.value = UserSession(isAuthenticated = false)
    }

    private fun mapFirebaseUser(user: com.google.firebase.auth.FirebaseUser?): UserSession =
        user?.let {
            UserSession(
                uid = it.uid,
                email = it.email,
                displayName = it.displayName,
                photoUrl = it.photoUrl?.toString(),
                isAuthenticated = true,
                isAuthenticating = false
            )
        } ?: UserSession(isAuthenticated = false)
}
