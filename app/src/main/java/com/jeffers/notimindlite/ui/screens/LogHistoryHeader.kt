package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val DAYS_PER_WEEK = 7L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogHistoryCardHeader(state: com.jeffers.notimindlite.ui.screens.LogHistoryCardState) {
    val item = state.item

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            AppIconImage(
                appIconUri = item.appIconUri,
                // Decorative: the app name is announced immediately after.
                contentDescriptionRes = null
            )
            if (!item.appIconUri.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = item.appName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            NotificationTimestamp(postTimeMillis = item.postTime, formatter = state.dateTimeFormatter)
        }

        LogHistoryHeaderActions(state)
    }
}

/**
 * When something arrived, visible without expanding the card. The persona's core question
 * is "where is the message from yesterday", which a title-only row cannot answer.
 */
@Composable
private fun NotificationTimestamp(postTimeMillis: Long, formatter: DateTimeFormatter) {
    val elapsedMinutes = remember(postTimeMillis) {
        ((System.currentTimeMillis() - postTimeMillis) / MILLIS_PER_MINUTE).coerceAtLeast(0)
    }
    val text = when {
        elapsedMinutes < 1 -> {
            stringResource(R.string.time_just_now)
        }

        elapsedMinutes < MINUTES_PER_HOUR -> {
            stringResource(R.string.time_minutes_ago, elapsedMinutes)
        }

        elapsedMinutes < MINUTES_PER_HOUR * HOURS_PER_DAY -> {
            stringResource(R.string.time_hours_ago, elapsedMinutes / MINUTES_PER_HOUR)
        }

        elapsedMinutes < MINUTES_PER_HOUR * HOURS_PER_DAY * DAYS_PER_WEEK -> {
            stringResource(
                R.string.time_days_ago,
                elapsedMinutes / (MINUTES_PER_HOUR * HOURS_PER_DAY)
            )
        }

        else -> {
            formatter.format(
                Instant.ofEpochMilli(postTimeMillis).atZone(ZoneId.systemDefault())
            )
        }
    }
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
