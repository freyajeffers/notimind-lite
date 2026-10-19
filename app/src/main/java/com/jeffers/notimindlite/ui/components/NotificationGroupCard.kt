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
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.screens.appIconImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Renders an expandable group card containing child notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationGroupCard(
    group: UiNotificationGroup,
    dateTimeFormatter: DateTimeFormatter,
    dao: NotificationDao,
    isGroupExpanded: Boolean,
    onToggleGroupExpand: () -> Unit,
    renderChildCard: @Composable (NotificationEntity) -> Unit,
    modifier: Modifier = Modifier,
    showAppIcons: Boolean = true,
    compactMode: Boolean = false
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
            GroupHeader(
                group = group,
                formattedTime = formattedTime,
                latestItem = latestItem,
                isGroupExpanded = isGroupExpanded,
                showAppIcons = showAppIcons,
                onToggleGroupExpand = onToggleGroupExpand,
                onTogglePinned = {
                    scope.launch {
                        val newPinned = !group.isPinned
                        val keys = group.items.map { it.key }
                        dao.updatePinnedStatusBatch(keys, newPinned)
                    }
                }
            )

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
                        key(childItem.key) {
                            renderChildCard(childItem)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupHeader(
    group: UiNotificationGroup,
    formattedTime: String,
    latestItem: NotificationEntity?,
    isGroupExpanded: Boolean,
    showAppIcons: Boolean,
    onToggleGroupExpand: () -> Unit,
    onTogglePinned: () -> Unit
) {
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
            if (showAppIcons) appIconImage(appIconUri = group.appIconUri)
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

        GroupHeaderActions(
            isPinned = group.isPinned,
            isGroupExpanded = isGroupExpanded,
            onToggleGroupExpand = onToggleGroupExpand,
            onTogglePinned = onTogglePinned
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupHeaderActions(
    isPinned: Boolean,
    isGroupExpanded: Boolean,
    onToggleGroupExpand: () -> Unit,
    onTogglePinned: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val pinAllTooltipRes = if (isPinned) {
            R.string.notification_group_unpin_all
        } else {
            R.string.notification_group_pin_all
        }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(stringResource(pinAllTooltipRes))
                }
            },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = onTogglePinned,
                modifier = Modifier.size(28.dp)
            ) {
                val pinIcon = if (isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder
                val pinDescRes = if (isPinned) {
                    R.string.notification_group_unpin
                } else {
                    R.string.notification_group_pin
                }
                Icon(
                    imageVector = pinIcon,
                    contentDescription = stringResource(pinDescRes),
                    tint = if (isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        val expandTooltipRes = if (isGroupExpanded) {
            R.string.notification_group_collapse
        } else {
            R.string.notification_group_expand
        }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(stringResource(expandTooltipRes))
                }
            },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = onToggleGroupExpand,
                modifier = Modifier.size(28.dp)
            ) {
                val expandIcon = if (isGroupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore
                val expandDescRes = if (isGroupExpanded) {
                    R.string.notification_group_collapse_short
                } else {
                    R.string.notification_group_expand_short
                }
                Icon(
                    imageVector = expandIcon,
                    contentDescription = stringResource(expandDescRes),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
