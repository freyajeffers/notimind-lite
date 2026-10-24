package com.jeffers.notimindlite.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun ActiveNotificationHeader(config: com.jeffers.notimindlite.ui.screens.LogNotificationCardConfig) {
    val item = config.item

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (config.showAppIcon) appIconImage(appIconUri = item.appIconUri)
            if (config.showAppIcon && !item.appIconUri.isNullOrEmpty()) Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.appName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        notificationHeaderActions(config)
    }
}

@Composable
private fun NotificationHeaderActions(config: LogNotificationCardConfig) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        notificationPinButton(config)
        Spacer(modifier = Modifier.width(6.dp))
        notificationDismissedStatus(config.item)
        if (config.item.isDismissed) Spacer(modifier = Modifier.width(6.dp))
        notificationOpenButton(config.item)
    }
}

@Composable
private fun NotificationPinButton(config: LogNotificationCardConfig) {
    val item = config.item
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            androidx.compose.material3.TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(if (item.isPinned) "Unpin notification" else "Pin notification") } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = { scope.launch { config.dao.updatePinnedStatus(item.key, !item.isPinned) } },
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
    }
}

@Composable
private fun NotificationDismissedStatus(item: com.jeffers.notimindlite.data.local.NotificationEntity) {
    if (!item.isDismissed) return
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = stringResource(id = getReasonLabel(item.dismissReason)),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NotificationOpenButton(item: com.jeffers.notimindlite.data.local.NotificationEntity) {
    val context = androidx.compose.ui.platform.LocalContext.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            androidx.compose.material3.TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text("Open notification") } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = {
                com.jeffers.notimindlite.util.NotificationLauncher.launchNotification(
                    context,
                    item.packageName,
                    item.key,
                    item.intentUri
                )
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open notification"
            )
        }
    }
}
