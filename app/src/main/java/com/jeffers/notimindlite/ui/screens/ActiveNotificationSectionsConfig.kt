package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import com.jeffers.notimindlite.domain.search.HybridSearchEngine

internal data class ActiveNotificationSectionsConfig(
    val sectionOrder: List<NotificationSection>,
    val expandedSection: String,
    val pinnedNotifs: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
    val activeNotifs: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
    val filteredNotifs: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
    val recentlyDismissed: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
    val lostNotifs: List<com.jeffers.notimindlite.data.local.NotificationEntity>,
    val selectedPackages: List<String>?,
    val debouncedSearchQuery: String,
    val groupByApp: Boolean,
    val sortOrder: String,
    val collapsedGroups: Set<String>,
    val setCollapsedGroups: (Set<String>) -> Unit,
    val expandedCards: Set<String>,
    val setExpandedCards: (Set<String>) -> Unit,
    val dateTimeFormatter: java.time.format.DateTimeFormatter,
    val dao: com.jeffers.notimindlite.data.local.NotificationDao,
    val showAppIcons: Boolean,
    val compactMode: Boolean,
    val previewLength: Int,
    val toggleSection: (String) -> Unit
)

internal fun LazyListScope.renderNotificationSections(config: ActiveNotificationSectionsConfig) {
    val sectionInputs = NotificationSectionItems(
        pinned = config.pinnedNotifs,
        active = config.activeNotifs,
        filtered = config.filteredNotifs,
        dismissed = config.recentlyDismissed,
        lost = config.lostNotifs
    )
    config.sectionOrder.forEach { section ->
        val isExpanded = config.expandedSection == section.keyName
        val rawItemsList = sectionItems(section, sectionInputs)
        val filteredList = if (!config.selectedPackages.isNullOrEmpty()) {
            rawItemsList.filter { config.selectedPackages.contains(it.packageName) }
        } else {
            rawItemsList
        }
        val itemsList = if (config.debouncedSearchQuery.isBlank()) {
            filteredList
        } else {
            HybridSearchEngine.searchAndRankBlocking(filteredList, config.debouncedSearchQuery)
        }
        notificationSection(
            NotificationSectionRenderConfig(
                section = section,
                isExpanded = isExpanded,
                itemsList = itemsList,
                groupByApp = config.groupByApp,
                sortOrder = config.sortOrder,
                collapsedGroups = config.collapsedGroups,
                onToggleSection = config.toggleSection,
                onToggleGroupExpand = { groupKey ->
                    config.setCollapsedGroups(
                        if (config.collapsedGroups.contains(groupKey)) {
                            config.collapsedGroups - groupKey
                        } else {
                            config.collapsedGroups + groupKey
                        }
                    )
                },
                expandedCards = config.expandedCards,
                expandedCardsUpdater = config.setExpandedCards,
                dateTimeFormatter = config.dateTimeFormatter,
                dao = config.dao,
                highlightQuery = config.debouncedSearchQuery,
                showAppIcon = config.showAppIcons,
                compactMode = config.compactMode,
                previewLength = config.previewLength
            )
        )
    }
}
