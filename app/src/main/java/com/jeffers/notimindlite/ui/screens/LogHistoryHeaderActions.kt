package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogHistoryHeaderActions(state: LogHistoryCardState) {
    val item = state.item
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinTooltipRes = when {
        state.isExpanded && item.isPinned -> com.jeffers.notimindlite.R.string.notification_detail_unpin
        state.isExpanded -> com.jeffers.notimindlite.R.string.notification_detail_pin
        item.isPinned -> com.jeffers.notimindlite.R.string.log_history_unpin_tooltip
        else -> com.jeffers.notimindlite.R.string.log_history_pin_tooltip
    }
    val pinDescriptionRes = when {
        state.isExpanded && item.isPinned -> com.jeffers.notimindlite.R.string.notification_detail_unpin_short
        state.isExpanded -> com.jeffers.notimindlite.R.string.notification_detail_pin_short
        item.isPinned -> com.jeffers.notimindlite.R.string.log_history_unpin_desc
        else -> com.jeffers.notimindlite.R.string.log_history_pin_desc
    }
    val openTooltipRes = if (state.isExpanded) {
        com.jeffers.notimindlite.R.string.notification_detail_open
    } else {
        com.jeffers.notimindlite.R.string.notification_open_tooltip
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(stringResource(pinTooltipRes)) } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = { scope.launch { state.dao.updatePinnedStatus(item.key, !item.isPinned) } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (item.isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(pinDescriptionRes),
                    tint = if (item.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        DismissStatusBadge(item)
        Spacer(modifier = Modifier.width(6.dp))
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(stringResource(openTooltipRes)) } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = {
                    com.jeffers.notimindlite.util.NotificationLauncher.launchNotification(
                        context, item.packageName, item.key, item.intentUri
                    )
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(com.jeffers.notimindlite.R.string.log_history_open_desc)
                )
            }
        }
    }
}
