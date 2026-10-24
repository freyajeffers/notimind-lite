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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogHistoryHeaderActions(state: LogHistoryCardState) {
    val item = state.item
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { scope.launch { state.dao.updatePinnedStatus(item.key, !item.isPinned) } },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (item.isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (item.isPinned) "Unpin" else "Pin",
                tint = if (item.isPinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        dismissStatusBadge(item)
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
            onClick = {
                com.jeffers.notimindlite.util.NotificationLauncher.launchNotification(
                    context, item.packageName, item.key, item.intentUri
                )
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open notification")
        }
    }
}
