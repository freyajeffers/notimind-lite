package com.jeffers.notimindlite.data.sync

import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.NotificationEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val DEFAULT_UPDATE_COUNT = 1

fun record(userId: String, profileId: String, entity: NotificationEntity) = JSONObject()
    .put("user_id", userId)
    .put("profile_id", profileId)
    .put("notification_key", entity.key)
    .put("updated_at", entity.lastUpdatedTime)
    .put("payload", payload(entity))

fun payload(e: NotificationEntity) = JSONObject()
    .put("key", e.key)
    .put("packageName", e.packageName)
    .put("appName", e.appName)
    .put("appIconUri", e.appIconUri ?: JSONObject.NULL)
    .put("title", e.title)
    .put("content", e.content)
    .put("postTime", e.postTime)
    .put("lastUpdatedTime", e.lastUpdatedTime)
    .put("updateCount", e.updateCount)
    .put("isDismissed", e.isDismissed)
    .put("isPersistent", e.isPersistent)
    .put("isRead", e.isRead)
    .put("isGroupSummary", e.isGroupSummary)
    .put("category", e.category ?: JSONObject.NULL)
    .put("channelId", e.channelId ?: JSONObject.NULL)
    .put("subText", e.subText ?: JSONObject.NULL)
    .put("bigText", e.bigText ?: JSONObject.NULL)
    .put("inboxLinesJson", e.inboxLinesJson ?: JSONObject.NULL)
    .put("priority", e.priority)
    .put("groupKey", e.groupKey ?: JSONObject.NULL)
    .put("isOngoing", e.isOngoing)
    .put("isClearable", e.isClearable)
    .put("actionsCount", e.actionsCount)
    .put("dismissReason", e.dismissReason ?: JSONObject.NULL)
    .put("dismissTime", e.dismissTime ?: JSONObject.NULL)
    .put("intentUri", e.intentUri ?: JSONObject.NULL)
    .put("isPinned", e.isPinned)
    .put("actionLabels", e.actionLabels ?: JSONObject.NULL)
    .put("smallIconRes", e.smallIconRes)
    .put("syncStatus", e.syncStatus.name)
    .put("lastSyncedAt", e.lastSyncedAt)

fun entity(p: JSONObject): NotificationEntity? = p.optString("key").takeIf { it.isNotBlank() }?.let {
    NotificationEntity(
        key = it,
        packageName = p.optString("packageName"),
        appName = p.optString("appName"),
        appIconUri = p.optNullableString("appIconUri"),
        title = p.optString("title"),
        content = p.optString("content"),
        postTime = p.optLong("postTime"),
        lastUpdatedTime = p.optLong("lastUpdatedTime"),
        updateCount = p.optInt("updateCount", DEFAULT_UPDATE_COUNT),
        isDismissed = p.optBoolean("isDismissed"),
        isPersistent = p.optBoolean("isPersistent"),
        isRead = p.optBoolean("isRead"),
        isGroupSummary = p.optBoolean("isGroupSummary"),
        category = p.optNullableString("category"),
        channelId = p.optNullableString("channelId"),
        subText = p.optNullableString("subText"),
        bigText = p.optNullableString("bigText"),
        inboxLinesJson = p.optNullableString("inboxLinesJson"),
        priority = p.optInt("priority"),
        groupKey = p.optNullableString("groupKey"),
        isOngoing = p.optBoolean("isOngoing"),
        isClearable = p.optBoolean("isClearable", true),
        actionsCount = p.optInt("actionsCount"),
        dismissReason = p.optNullableInt("dismissReason"),
        dismissTime = p.optNullableLong("dismissTime"),
        intentUri = p.optNullableString("intentUri"),
        isPinned = p.optBoolean("isPinned"),
        actionLabels = p.optNullableString("actionLabels"),
        smallIconRes = p.optInt("smallIconRes"),
        lastSyncedAt = p.optLong("lastSyncedAt")
    )
}

fun request(
    method: String,
    path: String,
    token: String,
    body: String? = null,
    prefer: String? = null
): String {
    val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.setRequestProperty("Content-Type", "application/json")
    prefer?.let { connection.setRequestProperty("Prefer", it) }
    if (body != null) {
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray()) }
    }
    val responseCode = connection.responseCode
    val stream = if (responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
        connection.inputStream
    } else {
        connection.errorStream
    }
    val response = stream.bufferedReader().use { it.readText() }
    if (responseCode !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
        error("Supabase HTTP $responseCode: $response")
    }
    return response
}

fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

// JSONObject helpers
fun JSONObject.optNullableString(name: String) = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

fun JSONObject.optNullableInt(name: String) = if (isNull(name)) null else optInt(name)

fun JSONObject.optNullableLong(name: String) = if (isNull(name)) null else optLong(name)
