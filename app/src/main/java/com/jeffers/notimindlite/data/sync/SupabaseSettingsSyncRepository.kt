package com.jeffers.notimindlite.data.sync

import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.auth.SupabaseAuthManager
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSettingsSyncRepository(private val auth: SupabaseAuthManager) : SettingsSyncRepository {
    override suspend fun restoreOrUpload(preferences: PreferencesRepository, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Supabase session required"))
            val path = "/rest/v1/user_settings?user_id=eq.${java.net.URLEncoder.encode(userId, "UTF-8")}&select=settings"
            val response = try {
                request("GET", path, token)
            } catch (e: IllegalStateException) {
                if (!e.message.orEmpty().contains("HTTP 401")) throw e
                val refreshed = auth.refreshSession().getOrThrow()
                request("GET", path, refreshed)
            }
            val rows = JSONArray(if (response.isBlank()) "[]" else response)
            val local = preferences.exportSyncableSettings()
            if (rows.length() == 0) {
                request("POST", "/rest/v1/user_settings", token, JSONObject().put("user_id", userId).put("settings", local).toString(), "return=minimal")
            } else {
                preferences.importSyncableSettings(rows.getJSONObject(0).optJSONObject("settings") ?: JSONObject())
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun request(method: String, path: String, token: String, body: String? = null, prefer: String? = null): String {
        val c = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", "application/json")
        prefer?.let { c.setRequestProperty("Prefer", it) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val result = stream.bufferedReader().use { it.readText() }
        if (c.responseCode !in 200..299) error("Supabase HTTP ${c.responseCode}: $result")
        return result
    }
}
