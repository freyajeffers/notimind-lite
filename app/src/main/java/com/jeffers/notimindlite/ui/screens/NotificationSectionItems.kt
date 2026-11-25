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
    items: NotificationSectionItems,
    blockedPackages: Set<String> = emptySet()
): List<NotificationEntity> = when (section) {
    NotificationSection.PINNED -> items.pinned.filter { !it.isDismissed }

    NotificationSection.ACTIVE -> items.active.filter {
        !it.isDismissed && !it.isPinned &&
            (it.isRestoredNotification() || !it.isSystemFilteredNotification(blockedPackages))
    }

    NotificationSection.FILTERED -> items.filtered.filter {
        !it.isDismissed && !it.isPinned && !it.isRestoredNotification()
    }

    NotificationSection.DISMISSED -> items.dismissed.filter { it.isDismissed }

    NotificationSection.LOST -> items.lost.filter { it.isDismissed }
}.distinctBy { it.key }

private val RESTORED_NOTIFICATION_ID_PATTERN = Regex(
    """"notimindRestoredNotificationId"\s*:\s*\d+"""
)

private fun NotificationEntity.isRestoredNotification(): Boolean =
    restoreMetadataJson?.let(RESTORED_NOTIFICATION_ID_PATTERN::containsMatchIn) == true

/**
 * A notification is filtered when the user blocked its package, or when the notification
 * itself is system chatter. The blocked set comes from the user's editable blocklist, so a
 * package they un-block stops being hidden here too.
 */
private fun NotificationEntity.isSystemFilteredNotification(blockedPackages: Set<String>): Boolean =
    packageName in blockedPackages ||
        category in SYSTEM_NOTIFICATION_CATEGORIES ||
        priority < 0

private val SYSTEM_NOTIFICATION_CATEGORIES = setOf("sys", "service", "status", "progress")
