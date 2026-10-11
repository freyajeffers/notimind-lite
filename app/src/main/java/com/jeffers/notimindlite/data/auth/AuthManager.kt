package com.jeffers.notimindlite.data.auth

import android.content.Context
import android.content.MutableContextWrapper
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val credentialContext = MutableContextWrapper(context)
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

    suspend fun signInWithGoogle(webClientId: String): Result<UserSession> {
        _session.value = _session.value.copy(isAuthenticating = true, error = null)
        supabaseAuth.beginGoogleSignIn()
        return Result.success(_session.value)
    }

    suspend fun handleIntent(intent: Intent): Result<UserSession>? {
        val uri = intent.data ?: return null
        if (uri.scheme != "notimindlite" || uri.host != "supabase-callback") return null
        val result = supabaseAuth.completeCallback(uri)
        _session.value = result.getOrElse { _session.value.copy(isAuthenticating = false, error = it.localizedMessage ?: "Sign-In failed") }
        return result
    }

    fun signOut() {
        supabaseAuth.signOut()
        firebaseAuth.signOut()
        _session.value = UserSession(isAuthenticated = false)
    }

    private fun mapFirebaseUser(user: com.google.firebase.auth.FirebaseUser?): UserSession {
        return if (user != null) {
            UserSession(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isAuthenticated = true,
                isAuthenticating = false
            )
        } else {
            UserSession(isAuthenticated = false)
        }
    }
}
