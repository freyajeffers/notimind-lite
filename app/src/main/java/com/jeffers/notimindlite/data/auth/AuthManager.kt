package com.jeffers.notimindlite.data.auth

import android.content.Context
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
    private val _session = MutableStateFlow(mapFirebaseUser(firebaseAuth.currentUser))
    val session: StateFlow<UserSession> = _session.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _session.value = mapFirebaseUser(auth.currentUser)
        }
    }

    suspend fun signInWithGoogle(webClientId: String): Result<UserSession> {
        _session.value = _session.value.copy(isAuthenticating = true, error = null)
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

            val authResult = firebaseAuth.signInWithCredential(authCredential).await()
            val user = authResult.user
            val session = mapFirebaseUser(user)
            _session.value = session
            Result.success(session)
        } catch (e: GetCredentialException) {
            val session = _session.value.copy(isAuthenticating = false, error = e.localizedMessage ?: "Google Sign-In canceled")
            _session.value = session
            Result.failure(e)
        } catch (e: Exception) {
            val session = _session.value.copy(isAuthenticating = false, error = e.localizedMessage ?: "Sign-In failed")
            _session.value = session
            Result.failure(e)
        }
    }

    fun signOut() {
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
