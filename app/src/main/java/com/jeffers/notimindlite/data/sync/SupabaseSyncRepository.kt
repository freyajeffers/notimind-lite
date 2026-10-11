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
        val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Supabase session required"))
        val userId = auth.currentSession().uid ?: return@withContext Result.failure(IllegalStateException("Supabase user id missing"))
        try {
            val local = db.notificationDao().getAllNotificationsSync()
            val records = JSONArray()
            local.forEach { records.put(record(userId, it)) }
            if (records.length() > 0) request("POST", "/rest/v1/notification_records?on_conflict=user_id,profile_id,notification_key", token, records.toString(), "resolution=merge-duplicates,return=minimal")

            val remoteBody = request("GET", "/rest/v1/notification_records?profile_id=eq.${encode(profileId)}&select=payload", token)
            val remote = JSONArray(remoteBody)
            var imported = 0
            for (i in 0 until remote.length()) {
                val payload = remote.getJSONObject(i).optJSONObject("payload") ?: continue
                val entity = entity(payload) ?: continue
                if (db.notificationDao().getNotificationByKey(entity.key) == null) {
                    db.notificationDao().insert(entity)
                    imported++
                }
            }
            Result.success(local.size + imported)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseSync", "Notification sync failed", e)
            Result.failure(e)
        }
    }

    private fun record(userId: String, entity: NotificationEntity) = JSONObject()
        .put("user_id", userId)
        .put("profile_id", profileId)
        .put("notification_key", entity.key)
        .put("updated_at", entity.lastUpdatedTime)
        .put("payload", payload(entity))

    private fun payload(e: NotificationEntity) = JSONObject()
        .put("key", e.key).put("packageName", e.packageName).put("appName", e.appName)
        .put("appIconUri", e.appIconUri ?: JSONObject.NULL).put("title", e.title).put("content", e.content)
        .put("postTime", e.postTime).put("lastUpdatedTime", e.lastUpdatedTime).put("updateCount", e.updateCount)
        .put("isDismissed", e.isDismissed).put("isPersistent", e.isPersistent).put("isRead", e.isRead)
        .put("isGroupSummary", e.isGroupSummary).put("category", e.category ?: JSONObject.NULL)
        .put("channelId", e.channelId ?: JSONObject.NULL).put("subText", e.subText ?: JSONObject.NULL)
        .put("bigText", e.bigText ?: JSONObject.NULL).put("inboxLinesJson", e.inboxLinesJson ?: JSONObject.NULL)
        .put("priority", e.priority).put("groupKey", e.groupKey ?: JSONObject.NULL).put("isOngoing", e.isOngoing)
        .put("isClearable", e.isClearable).put("actionsCount", e.actionsCount)
        .put("dismissReason", e.dismissReason ?: JSONObject.NULL).put("dismissTime", e.dismissTime ?: JSONObject.NULL)
        .put("intentUri", e.intentUri ?: JSONObject.NULL).put("isPinned", e.isPinned)
        .put("actionLabels", e.actionLabels ?: JSONObject.NULL).put("smallIconRes", e.smallIconRes)
        .put("syncStatus", e.syncStatus.name).put("lastSyncedAt", e.lastSyncedAt)

    private fun entity(p: JSONObject): NotificationEntity? = p.optString("key").takeIf { it.isNotBlank() }?.let {
        NotificationEntity(
            key = it, packageName = p.optString("packageName"), appName = p.optString("appName"),
            appIconUri = p.optNullableString("appIconUri"), title = p.optString("title"), content = p.optString("content"),
            postTime = p.optLong("postTime"), lastUpdatedTime = p.optLong("lastUpdatedTime"), updateCount = p.optInt("updateCount", 1),
            isDismissed = p.optBoolean("isDismissed"), isPersistent = p.optBoolean("isPersistent"), isRead = p.optBoolean("isRead"),
            isGroupSummary = p.optBoolean("isGroupSummary"), category = p.optNullableString("category"), channelId = p.optNullableString("channelId"),
            subText = p.optNullableString("subText"), bigText = p.optNullableString("bigText"), inboxLinesJson = p.optNullableString("inboxLinesJson"),
            priority = p.optInt("priority"), groupKey = p.optNullableString("groupKey"), isOngoing = p.optBoolean("isOngoing"),
            isClearable = p.optBoolean("isClearable", true), actionsCount = p.optInt("actionsCount"),
            dismissReason = p.optNullableInt("dismissReason"), dismissTime = p.optNullableLong("dismissTime"), intentUri = p.optNullableString("intentUri"),
            isPinned = p.optBoolean("isPinned"), actionLabels = p.optNullableString("actionLabels"), smallIconRes = p.optInt("smallIconRes"),
            lastSyncedAt = p.optLong("lastSyncedAt")
        )
    }

    private fun request(method: String, path: String, token: String, body: String? = null, prefer: String? = null): String {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        prefer?.let { connection.setRequestProperty("Prefer", it) }
        if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toByteArray()) } }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("Supabase HTTP ${connection.responseCode}: $response")
        return response
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun JSONObject.optNullableString(name: String) = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
    private fun JSONObject.optNullableInt(name: String) = if (isNull(name)) null else optInt(name)
    private fun JSONObject.optNullableLong(name: String) = if (isNull(name)) null else optLong(name)
}
