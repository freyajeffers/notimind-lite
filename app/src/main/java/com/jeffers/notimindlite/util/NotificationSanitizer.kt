package com.jeffers.notimindlite.util

import android.app.Notification
import android.content.Context
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository

private const val STALE_NOTIFICATION_DAYS = 30L
private const val HOURS_PER_DAY = 24L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L
private const val MILLIS_PER_SECOND = 1000L

class NotificationSanitizer(
    private val context: Context,
    private val appIconUri: (String) -> String?
) {
    private val parsingHelper by lazy { NotificationParsingHelper(context.packageManager) }
    private val preferences by lazy { PreferencesRepository(context) }

    /**
     * Returns a fully-built NotificationEntity or null if the notification should be ignored.
     * This function keeps the number of return statements to two (null or entity) to satisfy
     * style limits.
     */
    fun sanitizeAndBuild(
        sbn: android.service.notification.StatusBarNotification,
        notification: Notification
    ): NotificationEntity? {
        val extras = notification.extras
        val parsed = parsingHelper.parseTitleAndContent(extras)
        val title = parsed.title
        val content = parsed.content
        val subText = parsed.subText
        val bigText = parsed.bigText
        val inboxLinesJson = parsingHelper.extractInboxLinesJson(extras)
        val postTime = sbn.postTime
        val now = System.currentTimeMillis()

        val san = com.jeffers.notimindlite.sanitization
            .SanitizationPipeline(context)
            .sanitize(sbn.packageName, title, content, subText, bigText)

        val finalTitle = san?.let {
            if (preferences.anonymizeTitles.value) "[REDACTED-TITLE]" else it.title
        }
        val sanitizedData = san?.let { sanitized ->
            finalTitle
                ?.takeUnless { titleToStore ->
                    isKnownSystemClutter(sbn.packageName, title, content) ||
                        shouldReject(titleToStore, sanitized.content, sanitized.subText, postTime, now)
                }?.let { titleToStore -> sanitized to titleToStore }
        } ?: return null
        val (sanitizedNotification, titleForStorage) = sanitizedData

        val packageName = sbn.packageName
        val isOngoing = sbn.isOngoing

        val entity = NotificationEntity(
            key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}",
            packageName = packageName,
            appName = parsingHelper.resolveAppName(packageName),
            appIconUri = appIconUri(packageName),
            title = titleForStorage,
            content = sanitizedNotification.content,
            postTime = postTime,
            lastUpdatedTime = now,
            updateCount = 1,
            isDismissed = false,
            isPersistent = isOngoing,
            isRead = false,
            isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            category = notification.category,
            channelId = notification.channelId,
            subText = sanitizedNotification.subText,
            bigText = sanitizedNotification.bigText,
            inboxLinesJson = inboxLinesJson,
            priority = runCatching {
                notification.javaClass.getField("priority").getInt(notification)
            }.getOrDefault(0),
            groupKey = sbn.groupKey,
            isOngoing = isOngoing,
            isClearable = sbn.isClearable,
            actionsCount = notification.actions?.size ?: 0,
            intentUri = parsingHelper.safeLaunchIntentUri(packageName),
            isPinned = false,
            actionLabels = parsingHelper.buildActionLabelsJson(notification.actions),
            smallIconRes = parsingHelper.safeSmallIconRes(notification),
            restoreMetadataJson = NotificationRestoreMetadataExtractor.capture(
                notification = notification,
                redactPii = preferences.securityManager.redactPii.value,
                anonymizeTitles = preferences.anonymizeTitles.value
            )
        )

        return entity
    }

    private fun isKnownSystemClutter(packageName: String, title: String, content: String): Boolean {
        val normalizedTitle = title.trim()
        val normalizedContent = content.trim()
        return when (packageName) {
            "com.android.systemui" -> normalizedTitle.equals("USB debugging connected", ignoreCase = true) &&
                normalizedContent.contains("USB debugging", ignoreCase = true)

            "android" -> normalizedTitle.equals("Charging connected", ignoreCase = true) &&
                normalizedContent.startsWith("Charging", ignoreCase = true)

            else -> false
        }
    }

    private fun shouldReject(
        title: String,
        content: String,
        subText: String?,
        postTime: Long,
        now: Long
    ): Boolean {
        val hasNoText = title.isBlank() && content.isBlank()
        val summaryRegex = Regex("""\d+\s+more\s+notifications?""", RegexOption.IGNORE_CASE)
        val isSummary = summaryRegex.matches(title) || summaryRegex.matches(content)
        val hasSummarySubText = subText != null && summaryRegex.matches(subText)
        val maxAgeMs = STALE_NOTIFICATION_DAYS * HOURS_PER_DAY * MINUTES_PER_HOUR *
            SECONDS_PER_MINUTE * MILLIS_PER_SECOND
        val isStale = postTime > 0 && now - postTime > maxAgeMs
        return hasNoText || isSummary || hasSummarySubText || isStale
    }
}
