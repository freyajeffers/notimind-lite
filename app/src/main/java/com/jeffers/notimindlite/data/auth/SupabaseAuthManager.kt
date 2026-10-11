package com.jeffers.notimindlite.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.jeffers.notimindlite.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Temporary PKCE Google authentication through Supabase Auth. */
class SupabaseAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    private var verifier: String?
        get() = prefs.getString("pkce_verifier", null)
        set(value) = prefs.edit().putString("pkce_verifier", value).apply()

    fun accessToken(): String? = readSecret("access_token")

    fun currentSession(): UserSession = readSecret("access_token")
        ?.let { decodeUser(it) }
        ?.copy(isAuthenticated = true)
        ?: UserSession()

    fun beginGoogleSignIn() {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
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
        val currentVerifier = verifier ?: return@withContext Result.failure(IllegalStateException("Missing PKCE verifier"))
        try {
            val connection = (URL(BuildConfig.SUPABASE_URL + "/auth/v1/token?grant_type=pkce").openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("auth_code", code).put("code_verifier", currentVerifier).toString().toByteArray())
            }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error(JSONObject(body).optString("msg", "Supabase sign-in failed"))
            val response = JSONObject(body)
            val token = response.getString("access_token")
            saveSecret("access_token", token)
            saveSecret("refresh_token", response.optString("refresh_token"))
            verifier = null
            Result.success(decodeUser(token).copy(isAuthenticated = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() { prefs.edit().clear().apply() }

    private fun secretKey(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun saveSecret(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString("secret_$name", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readSecret(name: String): String? = prefs.getString("secret_$name", null)?.let {
        runCatching {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    } ?: prefs.getString(name, null)

    private companion object { const val KEY_ALIAS = "notimind_supabase_session" }
    private fun decodeUser(token: String): UserSession {
        val parts = token.split('.')
        if (parts.size < 2) return UserSession(error = "Invalid Supabase session")
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
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
