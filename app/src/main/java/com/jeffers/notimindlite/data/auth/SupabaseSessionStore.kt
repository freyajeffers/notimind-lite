package com.jeffers.notimindlite.data.auth

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure storage and refresh helper for Supabase session tokens.
 */
class SupabaseSessionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)

    suspend fun refreshSessionIfNeeded(supabaseUrl: String, anonKey: String): Result<String> = withContext(Dispatchers.IO) {
        val refresh = readSecret("refresh_token") ?: return@withContext Result.failure(IllegalStateException("Missing refresh token"))
        try {
            val connection = (URL("$supabaseUrl/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("refresh_token", refresh).toString().toByteArray()) }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) return@withContext Result.failure(IOException(JSONObject(body).optString("msg", "Supabase refresh failed")))
            val response = JSONObject(body)
            val token = response.getString("access_token")
            saveSecret("access_token", token)
            saveSecret("refresh_token", response.optString("refresh_token"))
            Result.success(token)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveAccessToken(token: String) { saveSecret("access_token", token) }
    fun saveRefreshToken(token: String) { saveSecret("refresh_token", token) }
    fun readAccessToken(): String? = readSecret("access_token")

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
}
