package com.jeffers.notimindlite.data.sync

import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.auth.SupabaseAuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional Supabase backend. Firebase remains the default when the setting is off. */
class SupabaseSyncRepository(
    private val db: AppDatabase,
    private val auth: SupabaseAuthManager,
    private val profileId: String
) {
    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        val first = syncOnce()
        val error = first.exceptionOrNull()
        if (error?.message.orEmpty().contains("HTTP 401")) {
            auth.refreshSession().fold(
                onSuccess = { syncOnce() },
                onFailure = { Result.failure(it) }
            )
        } else {
            first
        }
    }

    private suspend fun syncOnce(): Result<Int> {
        val token = auth.accessToken()
        val userId = auth.currentSession().uid
        if (token == null || userId == null) {
            return Result.failure(IllegalStateException("Supabase session required"))
        }
        return try {
            val local = db.notificationDao().getAllNotificationsSync().distinctBy { it.key }

            val remote = fetchRemoteRecords(token)
            val remoteUpdated = remoteUpdatedByKey(remote)

            val toUpload = buildRecordsToUpload(local, userId, remoteUpdated)
            if (toUpload.length() > 0) {
                uploadRecords(token, toUpload)
            }

            val imported = importRemoteIfNewer(remote)
            Result.success(local.size + imported)
        } catch (e: java.io.IOException) {
            android.util.Log.e("SupabaseSync", "Notification sync failed (IO)", e)
            Result.failure(e)
        } catch (e: org.json.JSONException) {
            android.util.Log.e("SupabaseSync", "Notification sync failed (JSON)", e)
            Result.failure(e)
        }
    }

    private fun fetchRemoteRecords(token: String): JSONArray {
        val path = "/rest/v1/notification_records?profile_id=eq.${encode(profileId)}" +
            "&select=notification_key,updated_at,payload"
        val remoteBody = request(
            "GET",
            path,
            token
        )
        return JSONArray(remoteBody)
    }

    private fun remoteUpdatedByKey(remote: JSONArray): Map<String, Long> {
        val updated = mutableMapOf<String, Long>()
        for (i in 0 until remote.length()) {
            val row = remote.getJSONObject(i)
            updated[row.optString("notification_key")] = row.optLong("updated_at")
        }
        return updated
    }

    private fun buildRecordsToUpload(
        local: List<NotificationEntity>,
        userId: String,
        remoteUpdated: Map<String, Long>
    ): JSONArray {
        val records = JSONArray()
        local
            .filter { entity ->
                entity.syncStatus.name != "PENDING_DELETE" &&
                    entity.lastUpdatedTime > (remoteUpdated[entity.key] ?: Long.MIN_VALUE)
            }.forEach { entity -> records.put(record(userId, profileId, entity)) }
        return records
    }

    private fun uploadRecords(token: String, records: JSONArray) {
        request(
            "POST",
            "/rest/v1/notification_records?on_conflict=user_id,profile_id,notification_key",
            token,
            records.toString(),
            "resolution=merge-duplicates,return=minimal"
        )
    }

    private suspend fun importRemoteIfNewer(remote: JSONArray): Int {
        return (0 until remote.length())
            .mapNotNull { index ->
                val payload = remote.getJSONObject(index).optJSONObject("payload") ?: return@mapNotNull null
                val imported = entity(payload) ?: return@mapNotNull null
                val existing = db.notificationDao().getNotificationByKey(imported.key)
                if (existing == null || imported.lastUpdatedTime > existing.lastUpdatedTime) {
                    db.notificationDao().insert(imported)
                    imported
                } else {
                    null
                }
            }.size
    }
}
