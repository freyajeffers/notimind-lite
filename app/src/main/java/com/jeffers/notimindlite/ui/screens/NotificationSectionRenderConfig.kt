package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.jeffers.notimindlite.ui.components.NotificationGroupCard
import com.jeffers.notimindlite.ui.components.UiNotificationGroup
import com.jeffers.notimindlite.ui.components.groupNotifications

@Immutable
internal data class NotificationSectionRenderConfig(
    val section: NotificationSection,
    val isExpanded: Boolean,
    val itemsList: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
    val groupByApp: Boolean,
    val sortOrder: String,
    val collapsedGroups: Set<String>,
    val onToggleSection: (String) -> Unit,
    val onToggleGroupExpand: (String) -> Unit,
    val expandedCards: Set<String>,
    val expandedCardsUpdater: (Set<String>) -> Unit,
    val dateTimeFormatter: java.time.format.DateTimeFormatter,
    val dao: com.jeffers.notimindlite.data.local.NotificationDao,
    val highlightQuery: String,
    val showAppIcon: Boolean,
    val compactMode: Boolean,
    val previewLength: Int
)

internal fun LazyListScope.notificationSection(config: NotificationSectionRenderConfig) {
    val section = config.section
    stickyHeader(key = "sticky_header_${section.keyName}") {
        activeSectionHeader(section, config.isExpanded, config.itemsList.size) {
            config.onToggleSection(section.keyName)
        }
    }
    if (!config.isExpanded) return
    val groups = groupNotifications(config.itemsList, config.groupByApp, config.sortOrder)
    items(items = groups, key = { group -> "group_${section.keyName}_${group.groupKey}" }) { group ->
        notificationSectionGroup(config, group)
    }
}

@Composable
private fun notificationSectionGroup(
    config: NotificationSectionRenderConfig,
    group: UiNotificationGroup
) {
    val isGroupExpanded = !config.collapsedGroups.contains(group.groupKey)
    if (group.items.size == 1) {
        notificationSectionCard(config, group.items[0])
    } else {
        NotificationGroupCard(
            group = group,
            dateTimeFormatter = config.dateTimeFormatter,
            dao = config.dao,
            isGroupExpanded = isGroupExpanded,
            onToggleGroupExpand = { config.onToggleGroupExpand(group.groupKey) },
            renderChildCard = { childItem -> notificationSectionCard(config, childItem) }
        )
    }
}

@Composable
private fun notificationSectionCard(
    config: NotificationSectionRenderConfig,
    item: com.jeffers.notimindlite.data.local.NotificationEntity
) {
    val cardExpanded = config.expandedCards.contains(item.key)
    logNotificationCard(
        LogNotificationCardConfig(
            item = item,
            dateTimeFormatter = config.dateTimeFormatter,
            dao = config.dao,
            isExpanded = cardExpanded,
            highlightQuery = config.highlightQuery,
            onToggleExpand = {
                config.expandedCardsUpdater(
                    if (cardExpanded) config.expandedCards - item.key
                    else config.expandedCards + item.key
                )
            },
            showAppIcon = config.showAppIcon,
            compactMode = config.compactMode,
            previewLength = config.previewLength
        )
    )
}
