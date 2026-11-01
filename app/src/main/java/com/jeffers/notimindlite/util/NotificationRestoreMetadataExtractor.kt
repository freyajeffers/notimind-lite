package com.jeffers.notimindlite.util

import android.app.Notification
import android.app.Person
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import com.jeffers.notimindlite.sanitization.PiiRedactionEngine
import org.json.JSONArray
import org.json.JSONObject

/** Extracts bounded, safely persistable presentation data for reboot restoration. */
internal object NotificationRestoreMetadataExtractor {
    private const val MAX_MESSAGES = 25
    private const val MAX_MESSAGE_TEXT_CHARS = 1_024
    private const val MAX_SENDER_CHARS = 160
    private const val MAX_FIELD_TEXT_CHARS = 2_048
    private const val SHOW_COLORIZED_EXTRA = "android.colorized"
    private const val GROUP_ALERT_BEHAVIOR_EXTRA = "android.groupAlertBehavior"

    fun capture(
        notification: Notification,
        redactPii: Boolean,
        anonymizeTitles: Boolean
    ): String {
        val extras = notification.extras ?: Bundle()
        val metadata = JSONObject()
            .put("schemaVersion", 1)
            .put("color", notification.color)
            .put("visibility", notification.visibility)
            .put("number", notification.number)
            .put("when", notification.`when`)
            .put("showWhen", extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
            .put("usesChronometer", extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false))
            .put("chronometerCountDown", extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false))
            .put("localOnly", (notification.flags and Notification.FLAG_LOCAL_ONLY) != 0)
            .put("colorized", extras.getBoolean(SHOW_COLORIZED_EXTRA, false))

        extras.getString(Notification.EXTRA_TEMPLATE)?.let { metadata.put("template", it) }
        putText(metadata, "titleBig", extras.getCharSequence(Notification.EXTRA_TITLE_BIG), redactPii, anonymizeTitles)
        putText(metadata, "summaryText", extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT), redactPii)
        putText(
            metadata,
            "conversationTitle",
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            redactPii,
            anonymizeTitles
        )
        putText(metadata, "tickerText", notification.tickerText, redactPii)

        val messagingPerson = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        val messagingUserName = sanitizeText(messagingPerson?.name?.toString(), redactPii, MAX_SENDER_CHARS)
        messagingUserName?.let { metadata.put("messagingUserName", it) }
        if (extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) {
            metadata.put(
                "isGroupConversation",
                extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            )
        }

        captureProgress(extras, metadata)
        captureGroupAlertBehavior(extras, metadata)
        captureLargeIconResource(notification, metadata)
        metadata.put("messages", captureMessages(extras, redactPii))
        return metadata.toString()
    }

    private fun captureProgress(extras: Bundle, metadata: JSONObject) {
        if (!extras.containsKey(Notification.EXTRA_PROGRESS_MAX)) return
        metadata
            .put("progressMax", extras.getInt(Notification.EXTRA_PROGRESS_MAX))
            .put("progress", extras.getInt(Notification.EXTRA_PROGRESS))
            .put(
                "progressIndeterminate",
                extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            )
    }

    private fun captureGroupAlertBehavior(extras: Bundle, metadata: JSONObject) {
        if (extras.containsKey(GROUP_ALERT_BEHAVIOR_EXTRA)) {
            metadata.put(GROUP_ALERT_BEHAVIOR_EXTRA, extras.getInt(GROUP_ALERT_BEHAVIOR_EXTRA))
        }
    }

    private fun captureLargeIconResource(notification: Notification, metadata: JSONObject) {
        val icon = runCatching { notification.getLargeIcon() }.getOrNull() ?: return
        if (icon.type != Icon.TYPE_RESOURCE) return
        val resourceId = runCatching { icon.resId }.getOrDefault(0)
        val resourcePackage = runCatching { icon.resPackage }.getOrNull()
        if (resourceId > 0 && !resourcePackage.isNullOrBlank()) {
            metadata
                .put("largeIconResourceId", resourceId)
                .put("largeIconPackage", resourcePackage)
        }
    }

    private fun captureMessages(extras: Bundle, redactPii: Boolean): JSONArray {
        val entries = buildList {
            addAll(messagesFrom(extras, Notification.EXTRA_HISTORIC_MESSAGES).map { it to true })
            addAll(messagesFrom(extras, Notification.EXTRA_MESSAGES).map { it to false })
        }.takeLast(MAX_MESSAGES)
        return JSONArray().apply {
            entries.forEach { (message, historic) ->
                val text = sanitizeText(message.text?.toString(), redactPii, MAX_MESSAGE_TEXT_CHARS)
                    ?: return@forEach
                val sender = message.senderPerson?.name?.toString()
                val item = JSONObject()
                    .put("text", text)
                    .put("timestamp", message.timestamp)
                    .put("historic", historic)
                sanitizeText(sender, redactPii, MAX_SENDER_CHARS)?.let { item.put("sender", it) }
                put(item)
            }
        }
    }

    private fun messagesFrom(extras: Bundle, key: String): List<Notification.MessagingStyle.Message> =
        runCatching {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(key, Parcelable::class.java)
            )
        }.getOrDefault(emptyList())

    private fun putText(
        metadata: JSONObject,
        key: String,
        value: CharSequence?,
        redactPii: Boolean,
        anonymizeTitle: Boolean = false
    ) {
        val text = if (anonymizeTitle && !value.isNullOrBlank()) {
            "[REDACTED-TITLE]"
        } else {
            sanitizeText(value?.toString(), redactPii, MAX_FIELD_TEXT_CHARS)
        }
        text?.let { metadata.put(key, it) }
    }

    private fun sanitizeText(value: String?, redactPii: Boolean, maxChars: Int): String? {
        val bounded = value?.take(maxChars)?.takeIf(String::isNotBlank) ?: return null
        return if (redactPii) {
            PiiRedactionEngine.redact(bounded)?.takeIf(String::isNotBlank)
        } else {
            bounded
        }
    }
}
