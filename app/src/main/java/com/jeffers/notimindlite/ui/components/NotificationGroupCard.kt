@file:Suppress("MatchingDeclarationName")

package com.jeffers.notimindlite.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.screens.AppIconImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

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
fun groupNotifications(items: List<NotificationEntity>, groupByApp: Boolean = true, sortOrder: String = "newest"): List<UiNotificationGroup> {
    if (items.isEmpty()) return emptyList()
    return items
        .groupBy { if (groupByApp) it.packageName else (it.key ?: it.postTime.toString()) }
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
        }
        .sortedWith(compareByDescending<UiNotificationGroup> { it.isPinned }.let { comparator ->
            when (sortOrder) {
                "oldest" -> comparator.thenBy { it.latestPostTime }
                "app" -> comparator.thenBy { it.appName.lowercase() }
                else -> comparator.thenByDescending { it.latestPostTime }
            }
        })
}

/**
 * Renders an expandable group card containing child notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "FunctionNaming", "LongParameterList", "MaxLineLength")
fun NotificationGroupCard(
    group: UiNotificationGroup,
    dateTimeFormatter: DateTimeFormatter,
    dao: NotificationDao,
    isGroupExpanded: Boolean,
    onToggleGroupExpand: () -> Unit,
    renderChildCard: @Composable (NotificationEntity) -> Unit,
    showAppIcons: Boolean = true,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val latestItem = remember(group.items) { group.items.firstOrNull() }
    val formattedTime = remember(group.latestPostTime) {
        dateTimeFormatter.format(Instant.ofEpochMilli(group.latestPostTime))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(if (compactMode) 6.dp else 10.dp)) {
            // Group Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleGroupExpand() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showAppIcons) AppIconImage(appIconUri = group.appIconUri)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${group.items.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (latestItem != null && latestItem.title.isNotBlank()) {
                            Text(
                                text = latestItem.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(if (group.isPinned) "Unpin all in group" else "Pin all in group")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val newPinned = !group.isPinned
                                    val keys = group.items.map { it.key }
                                    dao.updatePinnedStatusBatch(keys, newPinned)
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (group.isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (group.isPinned) "Unpin Group" else "Pin Group",
                                tint = if (group.isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(if (isGroupExpanded) "Collapse group" else "Expand group")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onToggleGroupExpand,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isGroupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isGroupExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Child Notifications List
            AnimatedVisibility(
                visible = isGroupExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.items.forEach { childItem ->
                        renderChildCard(childItem)
                    }
                }
            }
        }
    }
}
