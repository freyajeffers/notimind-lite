package com.jeffers.notimindlite.data.sync

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SupabaseSyncUtilsTest {
    @Test
    fun record_includesUserProfileAndNotificationIdentity() {
        val notification = NotificationEntity(
            key = "notification-key",
            packageName = "com.example.app",
            appName = "Example",
            title = "Title",
            content = "Body",
            lastUpdatedTime = 1234L
        )

        val record = record("user-1", "profile-1", notification)

        assertEquals("user-1", record.getString("user_id"))
        assertEquals("profile-1", record.getString("profile_id"))
        assertEquals(notification.key, record.getString("notification_key"))
        assertEquals(1234L, record.getLong("updated_at"))
        assertEquals(notification.key, record.getJSONObject("payload").getString("key"))
    }

    @Test
    fun payload_preservesSyncFieldsAndExcludesLocalRestoreMetadata() {
        val notification = NotificationEntity(
            key = "notification-key",
            packageName = "com.example.app",
            appName = "Example",
            appIconUri = "content://icons/example",
            title = "Title",
            content = "Body",
            postTime = 100L,
            lastUpdatedTime = 200L,
            updateCount = 3,
            isDismissed = true,
            isPersistent = true,
            isRead = true,
            isGroupSummary = true,
            category = "message",
            channelId = "messages",
            subText = "subtext",
            bigText = "expanded body",
            inboxLinesJson = "[\"one\",\"two\"]",
            priority = 2,
            groupKey = "group-1",
            isOngoing = true,
            isClearable = false,
            actionsCount = 2,
            dismissReason = 4,
            dismissTime = 300L,
            intentUri = "example://open",
            isPinned = true,
            actionLabels = "Reply",
            smallIconRes = 9,
            restoreMetadataJson = "local-only-presentation-data",
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = 400L
        )

        val payload = payload(notification)

        assertEquals(notification.key, payload.getString("key"))
        assertEquals(notification.appIconUri, payload.getString("appIconUri"))
        assertEquals(notification.postTime, payload.getLong("postTime"))
        assertEquals(notification.lastUpdatedTime, payload.getLong("lastUpdatedTime"))
        assertEquals(notification.updateCount, payload.getInt("updateCount"))
        assertTrue(payload.getBoolean("isDismissed"))
        assertTrue(payload.getBoolean("isPersistent"))
        assertTrue(payload.getBoolean("isRead"))
        assertTrue(payload.getBoolean("isGroupSummary"))
        assertEquals(notification.category, payload.getString("category"))
        assertEquals(notification.channelId, payload.getString("channelId"))
        assertEquals(notification.subText, payload.getString("subText"))
        assertEquals(notification.bigText, payload.getString("bigText"))
        assertEquals(notification.inboxLinesJson, payload.getString("inboxLinesJson"))
        assertEquals(notification.priority, payload.getInt("priority"))
        assertEquals(notification.groupKey, payload.getString("groupKey"))
        assertTrue(payload.getBoolean("isOngoing"))
        assertFalse(payload.getBoolean("isClearable"))
        assertEquals(notification.actionsCount, payload.getInt("actionsCount"))
        assertEquals(notification.dismissReason, payload.getInt("dismissReason"))
        assertEquals(notification.dismissTime, payload.getLong("dismissTime"))
        assertEquals(notification.intentUri, payload.getString("intentUri"))
        assertTrue(payload.getBoolean("isPinned"))
        assertEquals(notification.actionLabels, payload.getString("actionLabels"))
        assertEquals(notification.smallIconRes, payload.getInt("smallIconRes"))
        assertEquals(SyncStatus.SYNCED.name, payload.getString("syncStatus"))
        assertEquals(notification.lastSyncedAt, payload.getLong("lastSyncedAt"))
        assertFalse(payload.has("restoreMetadataJson"))
    }

    @Test
    fun payload_encodesNullableColumnsAsJsonNull() {
        val notification = NotificationEntity(
            key = "nullable-fields",
            packageName = "com.example.app",
            appName = "Example",
            title = "Title",
            content = "Body"
        )

        val payload = payload(notification)

        listOf(
            "appIconUri",
            "category",
            "channelId",
            "subText",
            "bigText",
            "inboxLinesJson",
            "groupKey",
            "dismissReason",
            "dismissTime",
            "intentUri",
            "actionLabels"
        ).forEach { field -> assertEquals(JSONObject.NULL, payload.get(field)) }
    }

    @Test
    fun entity_restoresPayloadFieldsAndKeepsLocalOnlyDefaults() {
        val source = NotificationEntity(
            key = "round-trip",
            packageName = "com.example.app",
            appName = "Example",
            appIconUri = "content://icons/example",
            title = "Title",
            content = "Body",
            postTime = 100L,
            lastUpdatedTime = 200L,
            updateCount = 3,
            isDismissed = true,
            isPersistent = true,
            isRead = true,
            isGroupSummary = true,
            category = "message",
            channelId = "messages",
            subText = "subtext",
            bigText = "expanded body",
            inboxLinesJson = "[\"one\",\"two\"]",
            priority = 2,
            groupKey = "group-1",
            isOngoing = true,
            isClearable = false,
            actionsCount = 2,
            dismissReason = 4,
            dismissTime = 300L,
            intentUri = "example://open",
            isPinned = true,
            actionLabels = "Reply",
            smallIconRes = 9,
            restoreMetadataJson = "local-only-presentation-data",
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = 400L
        )

        val restored = entity(payload(source))

        assertNotNull(restored)
        assertEquals(source.key, restored?.key)
        assertEquals(source.packageName, restored?.packageName)
        assertEquals(source.appName, restored?.appName)
        assertEquals(source.appIconUri, restored?.appIconUri)
        assertEquals(source.title, restored?.title)
        assertEquals(source.content, restored?.content)
        assertEquals(source.postTime, restored?.postTime)
        assertEquals(source.lastUpdatedTime, restored?.lastUpdatedTime)
        assertEquals(source.updateCount, restored?.updateCount)
        assertEquals(source.isDismissed, restored?.isDismissed)
        assertEquals(source.isPersistent, restored?.isPersistent)
        assertEquals(source.isRead, restored?.isRead)
        assertEquals(source.isGroupSummary, restored?.isGroupSummary)
        assertEquals(source.category, restored?.category)
        assertEquals(source.channelId, restored?.channelId)
        assertEquals(source.subText, restored?.subText)
        assertEquals(source.bigText, restored?.bigText)
        assertEquals(source.inboxLinesJson, restored?.inboxLinesJson)
        assertEquals(source.priority, restored?.priority)
        assertEquals(source.groupKey, restored?.groupKey)
        assertEquals(source.isOngoing, restored?.isOngoing)
        assertEquals(source.isClearable, restored?.isClearable)
        assertEquals(source.actionsCount, restored?.actionsCount)
        assertEquals(source.dismissReason, restored?.dismissReason)
        assertEquals(source.dismissTime, restored?.dismissTime)
        assertEquals(source.intentUri, restored?.intentUri)
        assertEquals(source.isPinned, restored?.isPinned)
        assertEquals(source.actionLabels, restored?.actionLabels)
        assertEquals(source.smallIconRes, restored?.smallIconRes)
        assertEquals(source.lastSyncedAt, restored?.lastSyncedAt)
        assertNull(restored?.restoreMetadataJson)
        assertEquals(SyncStatus.PENDING_UPLOAD, restored?.syncStatus)
    }

    @Test
    fun entity_missingFieldsUseDocumentedDefaultsAndBlankKeysAreRejected() {
        val restored = entity(JSONObject().put("key", "minimal"))

        assertNotNull(restored)
        assertEquals("", restored?.packageName)
        assertEquals("", restored?.appName)
        assertEquals("", restored?.title)
        assertEquals("", restored?.content)
        assertEquals(0L, restored?.postTime)
        assertEquals(1, restored?.updateCount)
        assertTrue(restored?.isClearable == true)
        assertEquals(SyncStatus.PENDING_UPLOAD, restored?.syncStatus)
        assertNull(entity(JSONObject()))
        assertNull(entity(JSONObject().put("key", "  ")))
    }

    @Test
    fun nullableJsonHelpersHandleMissingNullBlankAndTypedValues() {
        val json = JSONObject()
            .put("explicitNull", JSONObject.NULL)
            .put("blank", "   ")
            .put("text", "value")
            .put("number", 7)
            .put("timestamp", 9L)

        assertNull(json.optNullableString("missing"))
        assertNull(json.optNullableString("explicitNull"))
        assertNull(json.optNullableString("blank"))
        assertEquals("value", json.optNullableString("text"))
        assertNull(json.optNullableInt("missing"))
        assertNull(json.optNullableInt("explicitNull"))
        assertEquals(7, json.optNullableInt("number"))
        assertNull(json.optNullableLong("missing"))
        assertNull(json.optNullableLong("explicitNull"))
        assertEquals(9L, json.optNullableLong("timestamp"))
    }

    @Test
    fun encode_usesUtf8FormEncodingForReservedCharacters() {
        assertEquals("a+b%2Fc", encode("a b/c"))
    }
}
