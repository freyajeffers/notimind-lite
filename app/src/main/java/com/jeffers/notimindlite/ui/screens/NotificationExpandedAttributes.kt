package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationEntity
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun NotificationExpandedAttributes(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val accessibilityHeading = stringResource(R.string.notification_section_attributes)
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
                text = stringResource(com.jeffers.notimindlite.R.string.notification_attributes_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = accessibilityHeading
                    heading()
                }
            )
            NotificationIdentityAttributes(item, dateTimeFormatter)
            NotificationStateAttributes(item)
            NotificationSyncAttributes(item, dateTimeFormatter)
        }
    }
}

@Composable
private fun NotificationIdentityAttributes(item: NotificationEntity, formatter: DateTimeFormatter) {
    AttributeRow(
        stringResource(R.string.notification_detail_package),
        item.packageName
    )
    if (item.appName.isNotBlank() && item.appName != item.packageName) {
        AttributeRow(
            stringResource(R.string.notification_detail_app_name),
            item.appName
        )
    }
    if (!item.channelId.isNullOrEmpty()) {
        AttributeRow(
            stringResource(R.string.notification_detail_channel_id),
            item.channelId
        )
    }
    if (!item.category.isNullOrEmpty()) {
        AttributeRow(
            stringResource(R.string.notification_attribute_category),
            item.category
        )
    }
    AttributeRow(
        stringResource(R.string.notification_detail_priority),
        priorityLabel(item.priority)
    )
    AttributeRow(
        stringResource(R.string.notification_attribute_time_received),
        formatter.format(Instant.ofEpochMilli(item.postTime))
    )
    if (item.lastUpdatedTime > 0 && item.lastUpdatedTime != item.postTime) {
        AttributeRow(
            stringResource(R.string.notification_attribute_last_updated),
            formatter.format(Instant.ofEpochMilli(item.lastUpdatedTime))
        )
    }
    if (item.updateCount > 1) {
        AttributeRow(
            stringResource(R.string.notification_attribute_update_count),
            item.updateCount.toString()
        )
    }
    if (item.dismissTime != null && item.dismissTime > 0) {
        AttributeRow(
            stringResource(R.string.notification_attribute_time_dismissed),
            formatter.format(Instant.ofEpochMilli(item.dismissTime))
        )
    }
    if (item.dismissReason != null) {
        AttributeRow(
            stringResource(R.string.notification_attribute_dismiss_reason),
            stringResource(getReasonLabel(item.dismissReason))
        )
    }
}

@Composable
private fun NotificationStateAttributes(item: NotificationEntity) {
    val yes = stringResource(R.string.notification_value_yes)
    val no = stringResource(R.string.notification_value_no)
    val read = stringResource(R.string.notification_value_read)

    if (item.isOngoing) {
        AttributeRow(stringResource(R.string.notification_attribute_ongoing), yes)
    }
    if (item.isPersistent) {
        AttributeRow(stringResource(R.string.notification_attribute_persistent), yes)
    }
    if (!item.isClearable) {
        AttributeRow(stringResource(R.string.notification_attribute_clearable), no)
    }
    if (item.isPinned) {
        AttributeRow(stringResource(R.string.notification_attribute_pinned), yes)
    }
    if (item.isRead) {
        AttributeRow(stringResource(R.string.notification_attribute_read_status), read)
    }
    if (item.isGroupSummary) {
        AttributeRow(stringResource(R.string.notification_attribute_group_summary), yes)
    }
    if (!item.groupKey.isNullOrEmpty()) {
        AttributeRow(stringResource(R.string.notification_attribute_group_key), item.groupKey)
    }
    if (item.actionsCount > 0) {
        AttributeRow(
            stringResource(R.string.notification_attribute_actions_count),
            item.actionsCount.toString()
        )
    }
    if (!item.actionLabels.isNullOrEmpty()) {
        AttributeRow(stringResource(R.string.notification_attribute_action_labels), item.actionLabels)
    }
    if (!item.intentUri.isNullOrEmpty()) {
        AttributeRow(stringResource(R.string.notification_attribute_intent_uri), item.intentUri)
    }
    if (item.smallIconRes != 0) {
        AttributeRow(
            stringResource(R.string.notification_attribute_small_icon_res),
            "0x${item.smallIconRes.toString(HEX_RADIX).uppercase()}"
        )
    }
    if (!item.appIconUri.isNullOrEmpty()) {
        AttributeRow(stringResource(R.string.notification_attribute_app_icon_uri), item.appIconUri)
    }
}

@Composable
private fun NotificationSyncAttributes(item: NotificationEntity, formatter: DateTimeFormatter) {
    AttributeRow(stringResource(R.string.notification_attribute_sync_status), item.syncStatus.name)
    if (item.lastSyncedAt > 0) {
        AttributeRow(
            stringResource(R.string.notification_attribute_last_synced),
            formatter.format(Instant.ofEpochMilli(item.lastSyncedAt))
        )
    }
    if (item.embedding != null) {
        val visibleIndexStatus = stringResource(R.string.notification_attribute_vector_indexed)
        val spokenIndexStatus = stringResource(R.string.notification_value_vector_indexed)
        AttributeRow(
            stringResource(R.string.notification_attribute_vector_embedding),
            visibleIndexStatus,
            valueContentDescription = spokenIndexStatus
        )
    }
    if (item.id > 0) {
        AttributeRow(
            stringResource(R.string.notification_attribute_database_id),
            stringResource(R.string.notification_attribute_database_id_value, item.id)
        )
    }
    AttributeRow(stringResource(R.string.notification_attribute_key), item.key)
}

@Composable
private fun priorityLabel(priority: Int): String = when (priority) {
    PRIORITY_MIN -> stringResource(R.string.notification_priority_min)
    PRIORITY_LOW -> stringResource(R.string.notification_priority_low)
    PRIORITY_DEFAULT -> stringResource(R.string.notification_priority_default)
    PRIORITY_HIGH -> stringResource(R.string.notification_priority_high)
    PRIORITY_MAX -> stringResource(R.string.notification_priority_max)
    else -> stringResource(R.string.notification_priority_unknown, priority)
}

@Composable
fun AttributeRow(label: String, value: String, valueContentDescription: String? = null) {
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
            modifier = Modifier
                .weight(valueWeight)
                .then(
                    valueContentDescription?.let { description ->
                        Modifier.semantics { contentDescription = description }
                    } ?: Modifier
                )
        )
    }
}

private const val PRIORITY_MIN = -2
private const val PRIORITY_LOW = -1
private const val PRIORITY_DEFAULT = 0
private const val PRIORITY_HIGH = 1
private const val PRIORITY_MAX = 2
private const val LABEL_WEIGHT = 0.35f
private const val VALUE_WEIGHT = 0.65f
private const val LABEL_ALPHA = 0.8f
private const val HEX_RADIX = 16
private val labelValueWeights: Pair<Float, Float> = LABEL_WEIGHT to VALUE_WEIGHT
