package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationEntity
import org.json.JSONArray
import java.time.format.DateTimeFormatter

@Composable
internal fun logHistoryExpandedContent(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter,
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
            text = highlightSearchText(item.content, highlightQuery),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.subText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        if (!item.bigText.isNullOrEmpty() && item.bigText != item.content) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.bigText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
        val inboxLines = remember(item.inboxLinesJson) { parseInboxLines(item.inboxLinesJson) }
        if (inboxLines.isNotEmpty()) inboxLinesPanel(inboxLines)
        Spacer(modifier = Modifier.height(8.dp))
        notificationExpandedAttributes(item = item, dateTimeFormatter = dateTimeFormatter)
    }
}

@Composable
private fun inboxLinesPanel(inboxLines: List<String>) {
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        shape = androidx.compose.foundation.shape
            .RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.notification_inbox_lines, inboxLines.size),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            inboxLines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun parseInboxLines(json: String?): List<String> {
    val value = json?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}
