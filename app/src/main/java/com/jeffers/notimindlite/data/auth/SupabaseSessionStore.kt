package com.jeffers.notimindlite.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure storage and refresh helper for Supabase session tokens.
 */
class SupabaseSessionStore(
    private val context: Context
) {
    private companion object {
        const val HTTP_SUCCESS_MIN = 200
        const val HTTP_SUCCESS_MAX = 299
        const val HTTP_CONNECT_TIMEOUT_MILLIS = 15_000
        const val HTTP_READ_TIMEOUT_MILLIS = 15_000
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val KEY_ALIAS = "notimind_supabase_session"
    }

    private val prefs = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)

    suspend fun refreshSessionIfNeeded(supabaseUrl: String, anonKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            val refresh = readSecret("refresh_token")
                ?: return@withContext Result.failure(IllegalStateException("Missing refresh token"))
            try {
                refreshRequest(supabaseUrl, anonKey, refresh)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: org.json.JSONException) {
                Result.failure(IOException("Invalid Supabase refresh response", e))
            }
        }

    private fun refreshRequest(url: String, anonKey: String, refresh: String): Result<String> {
        val endpoint = "$url/auth/v1/token?grant_type=refresh_token"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = HTTP_READ_TIMEOUT_MILLIS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("refresh_token", refresh).toString()
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                val message = runCatching { JSONObject(body).optString("msg") }
                    .getOrNull()
                    .orEmpty()
                    .ifBlank { "Supabase refresh failed (HTTP $responseCode)" }
                return Result.failure(IOException(message))
            }
            val response = JSONObject(body)
            val token = response.getString("access_token")
            saveSecret("access_token", token)
            saveSecret("refresh_token", response.optString("refresh_token"))
            return Result.success(token)
        } finally {
            connection.disconnect()
        }
    }

    fun saveAccessToken(token: String) {
        saveSecret("access_token", token)
    }

    fun saveRefreshToken(token: String) {
        saveSecret("refresh_token", token)
    }

    fun readAccessToken(): String? = readSecret("access_token")

    private fun secretKey(): SecretKey {
        val store = java.security.KeyStore
            .getInstance("AndroidKeyStore")
            .apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                val spec = KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                init(spec)
            }.generateKey()
    }

    private fun saveSecret(name: String, value: String) {
        val cipher = Cipher
            .getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString("secret_$name", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readSecret(name: String): String? {
        val encryptedValue = prefs.getString("secret_$name", null)
        if (encryptedValue != null) {
            return decryptSecret(encryptedValue)
        }

        val legacyValue = prefs.getString(name, null) ?: return null
        return runCatching {
            val cipher = Cipher
                .getInstance("AES/GCM/NoPadding")
                .apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val encrypted = cipher.iv + cipher.doFinal(legacyValue.toByteArray())
            val migrated =
                prefs
                    .edit()
                    .putString("secret_$name", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .remove(name)
                    .commit()
            legacyValue.takeIf { migrated }
        }.getOrNull()
    }

    private fun decryptSecret(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        Cipher
            .getInstance("AES/GCM/NoPadding")
            .apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(0, GCM_IV_BYTES))
                )
            }.doFinal(bytes.copyOfRange(GCM_IV_BYTES, bytes.size))
            .toString(Charsets.UTF_8)
    }.getOrNull()
}
