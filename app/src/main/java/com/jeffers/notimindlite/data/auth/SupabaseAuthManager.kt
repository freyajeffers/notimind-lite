package com.jeffers.notimindlite.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.jeffers.notimindlite.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Temporary PKCE Google authentication through Supabase Auth. */
class SupabaseAuthManager(private val context: Context) {
    private companion object {
        const val PKCE_VERIFIER_BYTES = 32
        const val HTTP_SUCCESS_MIN = 200
        const val HTTP_SUCCESS_MAX = 299
        const val JWT_PAYLOAD_INDEX = 1
    }
    private val prefs = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    private val sessionStore = SupabaseSessionStore(context)
    private var verifier: String?
        get() = prefs.getString("pkce_verifier", null)
        set(value) = prefs.edit().putString("pkce_verifier", value).apply()

    fun accessToken(): String? = sessionStore.readAccessToken()

    fun currentSession(): UserSession = sessionStore.readAccessToken()
        ?.let { decodeUser(it) }
        ?.copy(isAuthenticated = true)
        ?: UserSession()

    fun beginGoogleSignIn() {
        val bytes = ByteArray(PKCE_VERIFIER_BYTES).also { SecureRandom().nextBytes(it) }
        val value = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        verifier = value
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val url = BuildConfig.SUPABASE_URL + "/auth/v1/authorize" +
            "?provider=google" +
            "&redirect_to=" + encode(BuildConfig.SUPABASE_REDIRECT_URI) +
            "&code_challenge=" + encode(challenge) +
            "&code_challenge_method=S256"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun completeCallback(uri: Uri): Result<UserSession> = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: return@withContext Result.failure(
            IllegalArgumentException(uri.getQueryParameter("error_description") ?: "Supabase sign-in was canceled")
        )
        val currentVerifier = verifier ?: return@withContext Result.failure(
            IllegalStateException("Missing PKCE verifier")
        )
        try {
            val endpoint = BuildConfig.SUPABASE_URL + "/auth/v1/token?grant_type=pkce"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject()
                .put("auth_code", code)
                .put("code_verifier", currentVerifier)
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val responseCode = connection.responseCode
            val responseStream = if (responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = responseStream.bufferedReader().use { it.readText() }
            if (responseCode !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                error(JSONObject(body).optString("msg", "Supabase sign-in failed"))
            }
            val response = JSONObject(body)
            val token = response.getString("access_token")
            sessionStore.saveAccessToken(token)
            sessionStore.saveRefreshToken(response.optString("refresh_token"))
            verifier = null
            Result.success(decodeUser(token).copy(isAuthenticated = true))
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: org.json.JSONException) {
            Result.failure(e)
        }
    }

    suspend fun refreshSession(): Result<String> =
        sessionStore.refreshSessionIfNeeded(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    fun signOut() { prefs.edit().clear().apply() }

    private fun decodeUser(token: String): UserSession {
        val parts = token.split('.')
        if (parts.size < JWT_PAYLOAD_INDEX + 1) return UserSession(error = "Invalid Supabase session")
        val payload = String(
            Base64.decode(parts[JWT_PAYLOAD_INDEX], Base64.URL_SAFE or Base64.NO_WRAP),
            Charsets.UTF_8
        )
        val claims = JSONObject(payload)
        return UserSession(
            uid = claims.optString("sub").takeIf { it.isNotBlank() },
            email = claims.optString("email").takeIf { it.isNotBlank() },
            displayName = claims.optJSONObject("user_metadata")?.optString("full_name")
                ?.takeIf { it.isNotBlank() }
        )
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
