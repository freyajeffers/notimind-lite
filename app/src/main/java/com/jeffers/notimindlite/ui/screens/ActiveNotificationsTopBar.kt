package com.jeffers.notimindlite.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R

@Composable
internal fun ActiveNotificationsTopBar(
    selectedPackages: List<String>?,
    searchQuery: String,
    onClearFilters: () -> Unit,
    onOpenPackagePicker: () -> Unit,
    onToggleSearch: () -> Unit
) {
    if (!selectedPackages.isNullOrEmpty() || searchQuery.isNotBlank()) {
        IconButton(onClick = onClearFilters) {
            Icon(Icons.Default.FilterAltOff, contentDescription = "Clear filters")
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
