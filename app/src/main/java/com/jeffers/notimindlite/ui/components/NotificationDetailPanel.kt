package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationEntity
import java.time.format.DateTimeFormatter

/**
 * NotificationDetailPanel provides a structured breakdown of a notification's metadata.
 * It is displayed when a notification card in the LogHistoryScreen is expanded.
 */
@Composable
fun NotificationDetailPanel(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DetailRow(label = stringResource(R.string.notification_detail_package), value = item.packageName)
        DetailRow(label = stringResource(R.string.notification_detail_app_name), value = item.appName)
        DetailRow(label = stringResource(R.string.notification_detail_priority), value = item.priority.toString())
        DetailRow(
            label = stringResource(R.string.notification_detail_channel_id),
            value = item.channelId ?: stringResource(R.string.notification_detail_unknown)
        )
        DetailRow(
            label = stringResource(R.string.notification_detail_post_time),
            value = dateTimeFormatter.format(java.time.Instant.ofEpochMilli(item.postTime))
        )
        DetailRow(label = stringResource(R.string.notification_detail_dismiss_time), value = item.dismissTime?.let {
            dateTimeFormatter.format(java.time.Instant.ofEpochMilli(it))
        } ?: stringResource(R.string.notification_detail_not_available))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal
        )
    }
}
