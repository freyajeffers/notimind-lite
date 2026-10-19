package com.jeffers.notimindlite.ui.components

import com.jeffers.notimindlite.data.local.NotificationEntity

/**
 * UI model representing a group of notifications aggregated by groupKey or packageName.
 */
data class UiNotificationGroup(
    val groupKey: String,
    val appName: String,
    val packageName: String,
    val appIconUri: String?,
    val latestPostTime: Long,
    val isPinned: Boolean,
    val items: List<NotificationEntity>
)

/**
 * Utility function that groups a list of notifications by their groupKey (or packageName)
 * and sorts the resulting groups by latest post time descending (with pinned groups at top).
 */
fun groupNotifications(
    items: List<NotificationEntity>,
    groupByApp: Boolean = true,
    sortOrder: String = "newest"
): List<UiNotificationGroup> {
    if (items.isEmpty()) return emptyList()
    return items
        .groupBy { if (groupByApp) (it.groupKey ?: it.packageName) else it.key }
        .map { (groupKey, groupItems) ->
            val sortedItems = groupItems.sortedByDescending { it.postTime }
            val first = sortedItems.first()
            UiNotificationGroup(
                groupKey = groupKey,
                appName = first.appName,
                packageName = first.packageName,
                appIconUri = first.appIconUri,
                latestPostTime = sortedItems.maxOf { it.postTime },
                isPinned = sortedItems.any { it.isPinned },
                items = sortedItems
            )
        }.sortedWith(
            compareByDescending<UiNotificationGroup> { it.isPinned }.let { comparator ->
                when (sortOrder) {
                    "oldest" -> comparator.thenBy { it.latestPostTime }
                    "app" -> comparator.thenBy { it.appName.lowercase() }
                    else -> comparator.thenByDescending { it.latestPostTime }
                }
            }
        )
}
