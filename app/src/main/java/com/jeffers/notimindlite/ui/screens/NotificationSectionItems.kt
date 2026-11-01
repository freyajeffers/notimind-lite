package com.jeffers.notimindlite.ui.screens

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.RestoredNotificationState

internal data class NotificationSectionItems(
    val pinned: List<NotificationEntity>,
    val active: List<NotificationEntity>,
    val filtered: List<NotificationEntity>,
    val dismissed: List<NotificationEntity>,
    val lost: List<NotificationEntity>
)

internal fun sectionItems(
    section: NotificationSection,
    items: NotificationSectionItems
): List<NotificationEntity> = when (section) {
    NotificationSection.PINNED -> items.pinned.filter { !it.isDismissed }

    NotificationSection.ACTIVE -> items.active.filter {
        !it.isDismissed && !it.isPinned && (it.isRestoredNotification() || !it.isSystemFilteredNotification())
    }

    NotificationSection.FILTERED -> items.filtered.filter {
        !it.isDismissed && !it.isPinned && !it.isRestoredNotification()
    }

    NotificationSection.DISMISSED -> items.dismissed.filter { it.isDismissed }

    NotificationSection.LOST -> items.lost.filter { it.isDismissed }
}.distinctBy { it.key }

private val RESTORED_NOTIFICATION_ID_PATTERN = Regex(
    "\\\"${RestoredNotificationState.NOTIFICATION_ID_FIELD}\\\"\\s*:\\s*\\d+"
)

private fun NotificationEntity.isRestoredNotification(): Boolean =
    restoreMetadataJson?.let(RESTORED_NOTIFICATION_ID_PATTERN::containsMatchIn) == true

private fun NotificationEntity.isSystemFilteredNotification(): Boolean =
    packageName in SYSTEM_NOTIFICATION_PACKAGES ||
        category in SYSTEM_NOTIFICATION_CATEGORIES ||
        priority < 0

private val SYSTEM_NOTIFICATION_PACKAGES = setOf(
    "android",
    "com.android.systemui",
    "com.google.android.googlequicksearchbox"
)

private val SYSTEM_NOTIFICATION_CATEGORIES = setOf("sys", "service", "status", "progress")
