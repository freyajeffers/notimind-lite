package com.jeffers.notimindlite.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.data.local.NotificationEntity
import org.json.JSONArray

@Composable
internal fun activeNotificationExpandedContent(
    item: NotificationEntity,
    dateTimeFormatter: java.time.format.DateTimeFormatter,
    highlightQuery: String
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = highlightSearchText(item.title, highlightQuery),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.subText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(item.subText, style = MaterialTheme.typography.bodySmall)
        }
        if (!item.bigText.isNullOrEmpty() && item.bigText != item.content) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.bigText, style = MaterialTheme.typography.bodySmall)
        }
        val inboxLines = remember(item.inboxLinesJson) { parseActiveInboxLines(item.inboxLinesJson) }
        if (inboxLines.isNotEmpty()) activeInboxLinesPanel(inboxLines)
        Spacer(modifier = Modifier.height(8.dp))
        notificationExpandedAttributes(item = item, dateTimeFormatter = dateTimeFormatter)
    }
}

@Composable
private fun activeInboxLinesPanel(inboxLines: List<String>) {
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Inbox Lines (${inboxLines.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            inboxLines.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun parseActiveInboxLines(json: String?): List<String> {
    val value = json?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
    }.onFailure { Log.w("ActiveNotifications", "Invalid inbox lines JSON", it) }.getOrDefault(emptyList())
}
