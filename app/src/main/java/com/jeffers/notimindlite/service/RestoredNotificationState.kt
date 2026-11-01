package com.jeffers.notimindlite.service

import com.jeffers.notimindlite.data.local.NotificationEntity
import org.json.JSONException
import org.json.JSONObject

/** Local-only state stored alongside restore presentation metadata. */
internal object RestoredNotificationState {
    const val NOTIFICATION_ID_FIELD = "notimindRestoredNotificationId"
    const val QUEUED_FIELD = "notimindRestoreQueued"
    private const val NOTIFICATION_ID_OFFSET = 1_000
    private const val POSITIVE_INT_MASK = 0x7FFFFFFF
    private const val MAX_NOTIFICATION_ID = Int.MAX_VALUE - NOTIFICATION_ID_OFFSET
    private const val DISMISS_REASON_1 = 1
    private const val DISMISS_REASON_2 = 2
    private const val DISMISS_REASON_3 = 3
    private const val DISMISS_REASON_12 = 12
    private const val DISMISS_REASON_19 = 19
    private const val DISMISS_REASON_23 = 23

    private val recentlyDismissedReasons = setOf(
        DISMISS_REASON_1,
        DISMISS_REASON_2,
        DISMISS_REASON_3,
        DISMISS_REASON_12,
        DISMISS_REASON_19,
        DISMISS_REASON_23
    )

    fun notificationId(metadataJson: String?): Int? = parse(metadataJson)
        ?.optInt(NOTIFICATION_ID_FIELD, 0)
        ?.takeIf { it >= NOTIFICATION_ID_OFFSET }

    fun isQueued(metadataJson: String?): Boolean = parse(metadataJson)?.optBoolean(QUEUED_FIELD, false) == true

    fun notificationIdFor(entityId: Long): Int {
        val positiveHash = entityId.hashCode() and POSITIVE_INT_MASK
        return positiveHash % MAX_NOTIFICATION_ID + NOTIFICATION_ID_OFFSET
    }

    fun withNotificationId(metadataJson: String?, notificationId: Int): String {
        require(notificationId >= NOTIFICATION_ID_OFFSET)
        val metadata = parse(metadataJson) ?: JSONObject()
        metadata.remove(QUEUED_FIELD)
        return metadata.put(NOTIFICATION_ID_FIELD, notificationId).toString()
    }

    fun withQueuedState(metadataJson: String?): String {
        val metadata = parse(metadataJson) ?: JSONObject()
        metadata.remove(NOTIFICATION_ID_FIELD)
        return metadata.put(QUEUED_FIELD, true).toString()
    }

    fun withoutNotificationId(metadataJson: String?): String? = withoutRestoreState(metadataJson)

    fun withoutRestoreState(metadataJson: String?): String? {
        val metadata = parse(metadataJson) ?: return metadataJson
        metadata.remove(NOTIFICATION_ID_FIELD)
        metadata.remove(QUEUED_FIELD)
        return metadata.takeIf { it.length() > 0 }?.toString()
    }

    fun canRestore(item: NotificationEntity): Boolean =
        item.isDismissed && item.dismissReason in recentlyDismissedReasons &&
            notificationId(item.restoreMetadataJson) == null && !isQueued(item.restoreMetadataJson)

    private fun parse(metadataJson: String?): JSONObject? {
        if (metadataJson.isNullOrBlank()) return null
        return try {
            JSONObject(metadataJson)
        } catch (_: JSONException) {
            null
        }
    }
}
