package com.jeffers.notimindlite.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationCompat.InboxStyle
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.sanitization.PiiRedactionEngine
import com.jeffers.notimindlite.util.AppIconCache
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import androidx.core.app.NotificationCompat.MessagingStyle.Message as MessagingMessage
import androidx.core.app.Person.Builder as PersonBuilder

/** Rebuilds the closest safe system-notification representation from persisted fields. */
internal object RestoredNotificationFactory {
    private const val MAX_INBOX_LINES = 5
    private const val RESTORED_LARGE_ICON_SIZE = 128
    private const val SMALL_ICON_DP_SIZE = 24f
    private const val FLAG_ACTIVITY_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun create(
        context: Context,
        channelId: String,
        item: BootConsolidationResult,
        originals: List<NotificationEntity>,
        notificationId: Int,
        redactPii: Boolean
    ): Notification {
        val primary = originals.firstOrNull()
        val restoreMetadata = parseRestoreMetadata(primary?.restoreMetadataJson)
        val summaryTitle = redact(item.summaryTitle, redactPii).orEmpty()
        val originalTitle = redact(primary?.title, redactPii).orEmpty()
        val title = if (originals.size > 1) {
            summaryTitle.removePrefix("[Grouped] ").ifBlank { originalTitle }
        } else {
            originalTitle.ifBlank { summaryTitle }
        }
        val content = redact(primary?.content, redactPii)
            ?.takeIf(String::isNotBlank)
            ?: redact(item.summaryContent, redactPii).orEmpty()
        val subText = redact(primary?.subText, redactPii)
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.restored_after_reboot)
        val appName = primary?.appName?.takeIf(String::isNotBlank) ?: item.appName

        val priority = (primary?.priority ?: NotificationCompat.PRIORITY_DEFAULT).coerceIn(
            NotificationCompat.PRIORITY_MIN,
            NotificationCompat.PRIORITY_MAX
        )
        val restoredWhen = restoreMetadata?.takeIf { it.has("when") }?.optLong("when")
            ?: primary?.postTime
            ?: System.currentTimeMillis()
        val builder = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setContentInfo(appName)
            .setWhen(restoredWhen)
            .setShowWhen(restoreMetadata?.optBoolean("showWhen", true) ?: true)
            .setPriority(priority)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)

        applyPresentationMetadata(builder, restoreMetadata)
        primary?.category?.takeIf(String::isNotBlank)?.let(builder::setCategory)
        primary?.let { notification ->
            resolveSmallIcon(context, notification)?.let(builder::setSmallIcon)
            val largeIcon = resolveLargeIcon(context, restoreMetadata)
                ?: AppIconCache.getIcon(context, notification.appIconUri, RESTORED_LARGE_ICON_SIZE)
            largeIcon?.let(builder::setLargeIcon)
            buildContentIntent(context, notification, notificationId)?.let(builder::setContentIntent)
            notification.groupKey?.takeIf(String::isNotBlank)?.let { groupKey ->
                builder.setGroup("restored:${notification.packageName}:$groupKey")
                builder.setGroupSummary(notification.isGroupSummary)
            }
        }

        val inboxLines = when {
            originals.size > 1 -> originals.takeLast(MAX_INBOX_LINES).mapNotNull { original ->
                val lineTitle = redact(original.title, redactPii).orEmpty()
                val lineContent = redact(original.content, redactPii).orEmpty()
                listOf(lineTitle, lineContent)
                    .filter(String::isNotBlank)
                    .joinToString(": ")
                    .takeIf(String::isNotBlank)
            }

            else -> parseInboxLines(primary?.inboxLinesJson, redactPii).takeLast(MAX_INBOX_LINES)
        }

        applyNotificationStyle(
            builder = builder,
            metadata = restoreMetadata,
            inboxLines = inboxLines,
            title = title,
            content = content,
            subText = subText,
            bigText = primary?.bigText,
            redactPii = redactPii,
            canRebuildMessagingStyle = originals.size == 1
        )

        if (originals.size > 1) builder.setNumber(originals.size)
        return builder.build().apply { flags = flags or Notification.FLAG_NO_CLEAR }
    }

    private data class RestoredMessage(
        val text: String,
        val sender: String?,
        val timestamp: Long,
        val historic: Boolean
    )

    private fun parseRestoreMetadata(json: String?): JSONObject? = try {
        json?.let(::JSONObject)
    } catch (_: org.json.JSONException) {
        null
    }

    private fun applyPresentationMetadata(
        builder: NotificationCompat.Builder,
        metadata: JSONObject?
    ) {
        metadata ?: return
        if (metadata.has("color")) metadata.optInt("color").takeIf { it != 0 }?.let(builder::setColor)
        if (metadata.has("visibility")) builder.setVisibility(metadata.optInt("visibility"))
        metadata.optInt("number").takeIf { it > 0 }?.let(builder::setNumber)
        metadata.optBoolean("localOnly", false).let(builder::setLocalOnly)
        if (metadata.has("colorized")) builder.setColorized(metadata.optBoolean("colorized"))
        metadata.stringOrNull("tickerText")?.let(builder::setTicker)
        if (metadata.has("android.groupAlertBehavior")) {
            val behavior = metadata.optInt("android.groupAlertBehavior")
            if (
                behavior in setOf(
                    Notification.GROUP_ALERT_ALL,
                    Notification.GROUP_ALERT_CHILDREN,
                    Notification.GROUP_ALERT_SUMMARY
                )
            ) {
                builder.setGroupAlertBehavior(behavior)
            }
        }
        if (metadata.has("progressMax")) {
            builder.setProgress(
                metadata.optInt("progressMax"),
                metadata.optInt("progress"),
                metadata.optBoolean("progressIndeterminate", false)
            )
        }
        builder
            .setUsesChronometer(metadata.optBoolean("usesChronometer", false))
            .setChronometerCountDown(metadata.optBoolean("chronometerCountDown", false))
    }

    private fun resolveLargeIcon(context: Context, metadata: JSONObject?): Bitmap? {
        metadata ?: return null
        val packageName = metadata.stringOrNull("largeIconPackage") ?: return null
        val resourceId = metadata.optInt("largeIconResourceId").takeIf { it > 0 } ?: return null
        return try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val drawable = ResourcesCompat.getDrawable(resources, resourceId, context.theme) ?: return null
            val bitmap = createBitmap(RESTORED_LARGE_ICON_SIZE, RESTORED_LARGE_ICON_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun applyNotificationStyle(
        builder: NotificationCompat.Builder,
        metadata: JSONObject?,
        inboxLines: List<String>,
        title: String,
        content: String,
        subText: String,
        bigText: String?,
        redactPii: Boolean,
        canRebuildMessagingStyle: Boolean
    ) {
        if (canRebuildMessagingStyle) {
            buildMessagingStyle(metadata, redactPii)?.let {
                builder.setStyle(it)
                return
            }
        }
        val restoredMessageLines = parseRestoredMessages(metadata, redactPii).takeLast(MAX_INBOX_LINES).map {
            listOfNotNull(it.sender, it.text).joinToString(": ")
        }
        val lines = inboxLines.ifEmpty { restoredMessageLines }
        val bigTitle = redact(metadata?.stringOrNull("titleBig"), redactPii) ?: title
        val summary = redact(metadata?.stringOrNull("summaryText"), redactPii) ?: subText
        if (lines.isNotEmpty()) {
            val style = InboxStyle()
                .setBigContentTitle(bigTitle)
                .setSummaryText(summary)
            lines.forEach(style::addLine)
            builder.setStyle(style)
            return
        }
        val expandedText = redact(bigText, redactPii)
            ?.takeIf(String::isNotBlank)
            ?: redact(content, redactPii)
        val needsExpandedStyle = expandedText != content || bigTitle != title || summary != subText
        if (expandedText != null && needsExpandedStyle) {
            val style = BigTextStyle()
                .setBigContentTitle(bigTitle)
                .setSummaryText(summary)
                .bigText(expandedText)
            builder.setStyle(style)
        }
    }

    private fun buildMessagingStyle(
        metadata: JSONObject?,
        redactPii: Boolean
    ): MessagingStyle? {
        if (metadata == null || metadata.stringOrNull("template")?.endsWith("MessagingStyle") != true) return null
        val messages = parseRestoredMessages(metadata, redactPii)
        if (messages.isEmpty()) return null
        val user = PersonBuilder()
            .setName(redact(metadata.stringOrNull("messagingUserName"), redactPii).orEmpty())
            .build()
        val style = MessagingStyle(user)
        redact(metadata.stringOrNull("conversationTitle"), redactPii)
            ?.takeIf(String::isNotBlank)
            ?.let(style::setConversationTitle)
        if (metadata.has("isGroupConversation")) {
            style.setGroupConversation(metadata.optBoolean("isGroupConversation"))
        }
        messages.forEach { message ->
            val sender = message.sender?.let { redact(it, redactPii) }?.takeIf(String::isNotBlank)
            val person = sender?.let { PersonBuilder().setName(it).build() }
            val compatMessage = MessagingMessage(message.text, message.timestamp, person)
            if (message.historic) style.addHistoricMessage(compatMessage) else style.addMessage(compatMessage)
        }
        return style
    }

    private fun parseRestoredMessages(metadata: JSONObject?, redactPii: Boolean): List<RestoredMessage> {
        val messages = metadata?.optJSONArray("messages") ?: return emptyList()
        return (0 until messages.length()).mapNotNull { index ->
            parseRestoredMessage(messages.optJSONObject(index), redactPii)
        }
    }

    private fun parseRestoredMessage(item: JSONObject?, redactPii: Boolean): RestoredMessage? {
        item ?: return null
        val text = redact(item.stringOrNull("text"), redactPii)?.takeIf(String::isNotBlank) ?: return null
        return RestoredMessage(
            text = text,
            sender = item.stringOrNull("sender"),
            timestamp = item.optLong("timestamp"),
            historic = item.optBoolean("historic", false)
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun resolveSmallIcon(context: Context, notification: NotificationEntity): IconCompat? {
        if (notification.smallIconRes <= 0) return null
        return try {
            val resources = context.packageManager.getResourcesForApplication(notification.packageName)
            val drawable = ResourcesCompat.getDrawable(resources, notification.smallIconRes, context.theme)
                ?: return null
            val density = context.resources.displayMetrics.density
            val iconSize = (SMALL_ICON_DP_SIZE * density).roundToInt().coerceAtLeast(1)
            val bitmap = createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            IconCompat.createWithBitmap(bitmap)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun buildContentIntent(
        context: Context,
        notification: NotificationEntity,
        notificationId: Int
    ): PendingIntent? {
        val launchIntent = try {
            context.packageManager.getLaunchIntentForPackage(notification.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return null

        launchIntent.addFlags(FLAG_ACTIVITY_FLAGS)
        return PendingIntent.getActivity(context, notificationId, launchIntent, PENDING_INTENT_FLAGS)
    }

    private fun parseInboxLines(json: String?, redactPii: Boolean): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val lines = try {
            JSONArray(json)
        } catch (_: org.json.JSONException) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until lines.length()) {
                val line = lines.opt(index) as? String ?: continue
                redact(line, redactPii)
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }

    private fun redact(value: String?, redactPii: Boolean): String? =
        if (redactPii) PiiRedactionEngine.redact(value) else value
}
