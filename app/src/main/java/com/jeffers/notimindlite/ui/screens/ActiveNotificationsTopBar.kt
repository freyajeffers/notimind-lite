package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.jeffers.notimindlite.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveNotificationsTopBar(
    selectedPackages: List<String>?,
    searchQuery: String,
    isGranted: Boolean,
    onClearFilters: () -> Unit,
    onOpenPackagePicker: () -> Unit,
    onToggleSearch: () -> Unit
) {
    val serviceTitle = stringResource(R.string.active_notifications_service_title)
    val serviceStatus = stringResource(
        if (isGranted) {
            R.string.active_notifications_service_status_active
        } else {
            R.string.active_notifications_service_status_required
        }
    )
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.active_notifications_title))
                Text(
                    text = "$serviceTitle: $serviceStatus",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            if (!selectedPackages.isNullOrEmpty() || searchQuery.isNotBlank()) {
                IconButton(onClick = onClearFilters) {
                    Icon(
                        Icons.Default.FilterAltOff,
                        contentDescription = stringResource(R.string.log_history_clear_filters)
                    )
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(id = R.string.active_notifications_filter_apps)) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onOpenPackagePicker) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(id = R.string.active_notifications_filter_apps),
                        tint = if (!selectedPackages.isNullOrEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(id = R.string.common_search)) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(id = R.string.common_search))
                }
            }
        }
    )
}
