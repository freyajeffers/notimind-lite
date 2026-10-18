package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.data.local.NotificationEntity
import java.time.Instant
import java.time.format.DateTimeFormatter


@Composable
fun notificationExpandedAttributes(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Notification Attributes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            notificationIdentityAttributes(item, dateTimeFormatter)
            notificationStateAttributes(item)
            notificationSyncAttributes(item, dateTimeFormatter)
        }
    }
}

@Composable
private fun notificationIdentityAttributes(item: NotificationEntity, formatter: DateTimeFormatter) {
    attributeRow("Package", item.packageName)
    if (item.appName.isNotBlank() && item.appName != item.packageName) {
        attributeRow("App Name", item.appName)
    }
    if (!item.channelId.isNullOrEmpty()) attributeRow("Channel ID", item.channelId)
    if (!item.category.isNullOrEmpty()) attributeRow("Category", item.category)
    attributeRow("Priority", getPriorityLabel(item.priority))
    attributeRow("Time Received", formatter.format(Instant.ofEpochMilli(item.postTime)))
    if (item.lastUpdatedTime > 0 && item.lastUpdatedTime != item.postTime) {
        attributeRow("Last Updated", formatter.format(Instant.ofEpochMilli(item.lastUpdatedTime)))
    }
    if (item.updateCount > 1) attributeRow("Update Count", "${item.updateCount}")
    if (item.dismissTime != null && item.dismissTime > 0) {
        attributeRow("Time Dismissed", formatter.format(Instant.ofEpochMilli(item.dismissTime)))
    }
    if (item.dismissReason != null) {
        attributeRow("Dismiss Reason", stringResource(getReasonLabel(item.dismissReason)))
    }
}

@Composable
private fun notificationStateAttributes(item: NotificationEntity) {
    if (item.isOngoing) attributeRow("Ongoing", "Yes")
    if (item.isPersistent) attributeRow("Persistent", "Yes")
    if (!item.isClearable) attributeRow("Clearable", "No")
    if (item.isPinned) attributeRow("Pinned", "Yes")
    if (item.isRead) attributeRow("Read Status", "Read")
    if (item.isGroupSummary) attributeRow("Group Summary", "Yes")
    if (!item.groupKey.isNullOrEmpty()) attributeRow("Group Key", item.groupKey)
    if (item.actionsCount > 0) attributeRow("Actions Count", "${item.actionsCount}")
    if (!item.actionLabels.isNullOrEmpty()) attributeRow("Action Labels", item.actionLabels)
    if (!item.intentUri.isNullOrEmpty()) attributeRow("Intent URI", item.intentUri)
    if (item.smallIconRes != 0) {
        attributeRow("Small Icon Res", "0x${item.smallIconRes.toString(HEX_RADIX).uppercase()}")
    }
    if (!item.appIconUri.isNullOrEmpty()) attributeRow("App Icon URI", item.appIconUri)
}

@Composable
private fun notificationSyncAttributes(item: NotificationEntity, formatter: DateTimeFormatter) {
    attributeRow("Sync Status", item.syncStatus.name)
    if (item.lastSyncedAt > 0) {
        attributeRow("Last Synced", formatter.format(Instant.ofEpochMilli(item.lastSyncedAt)))
    }
    if (item.embedding != null) attributeRow("Vector Embedding", "128-dim Indexed")
    if (item.id > 0) attributeRow("Database ID", "#${item.id}")
    attributeRow("Key", item.key)
}

@Composable
fun attributeRow(label: String, value: String) {
    val (labelWeight, valueWeight) = labelValueWeights
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LABEL_ALPHA),
            modifier = Modifier.weight(labelWeight)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(valueWeight)
        )
    }
}

private const val LABEL_WEIGHT = 0.35f
private const val VALUE_WEIGHT = 0.65f
private const val LABEL_ALPHA = 0.8f
private const val HEX_RADIX = 16
private val labelValueWeights: Pair<Float, Float> = LABEL_WEIGHT to VALUE_WEIGHT
