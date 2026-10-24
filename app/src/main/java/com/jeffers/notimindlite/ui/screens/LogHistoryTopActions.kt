package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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

data class LogHistoryTopActions(
    val selectedPackages: List<String>?,
    val selectedReasonFilter: Int?,
    val searchQuery: String,
    val isSearchFocused: Boolean,
    val isSearchVisible: Boolean,
    val focusManager: androidx.compose.ui.focus.FocusManager,
    val keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    val availableReasons: List<Int>,
    val availableApps: List<Pair<String, String>>,
    val showFilterMenu: Boolean,
    val setShowFilterMenu: (Boolean) -> Unit,
    val showPackagePicker: Boolean,
    val setShowPackagePicker: (Boolean) -> Unit,
    val setSelectedPackages: (List<String>?) -> Unit,
    val setSelectedReasonFilter: (Int?) -> Unit,
    val preferences: com.jeffers.notimindlite.data.local.PreferencesRepository
)

@Composable
fun LogHistoryTopActions(config: LogHistoryTopActions) {
    if (!config.selectedPackages.isNullOrEmpty() ||
        config.selectedReasonFilter != null || config.searchQuery.isNotBlank()
    ) {
        IconButton(onClick = {
            config.setSelectedPackages(null)
            config.setSelectedReasonFilter(null)
            config.focusManager.clearFocus(force = true)
            config.keyboardController?.hide()
        }) { Icon(Icons.Default.FilterAltOff, contentDescription = "Clear filters") }
    }
    logHistoryFilterMenu(config)
}

@Composable
private fun LogHistoryFilterMenu(config: LogHistoryTopActions) {
    Box {
        val hasActiveFilters = !config.selectedPackages.isNullOrEmpty() ||
            config.selectedReasonFilter != null
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above
            ),
            tooltip = { PlainTooltip { Text(stringResource(id = R.string.log_history_filter_title)) } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = { config.setShowFilterMenu(true) }) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(id = R.string.log_history_filter_title),
                    tint = if (hasActiveFilters) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        logHistoryFilterDropdown(config)
    }
}

@Composable
private fun LogHistoryFilterDropdown(config: LogHistoryTopActions) {
    DropdownMenu(
        expanded = config.showFilterMenu,
        onDismissRequest = { config.setShowFilterMenu(false) }
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        id = R.string.log_history_filter_app_count,
                        config.selectedPackages?.size?.toString()
                            ?: stringResource(R.string.common_all)
                    )
                )
            },
            onClick = {
                config.setShowFilterMenu(false)
                config.setShowPackagePicker(true)
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        id = R.string.log_history_filter_all_reasons,
                        if (config.selectedReasonFilter == null) "✓" else ""
                    )
                )
            },
            onClick = {
                config.setSelectedReasonFilter(null)
                config.setShowFilterMenu(false)
            }
        )
        config.availableReasons.forEach { reasonCode ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            id = R.string.log_history_filter_reason_item,
                            getReasonLabel(reasonCode),
                            reasonCode,
                            if (config.selectedReasonFilter == reasonCode) "✓" else ""
                        )
                    )
                },
                onClick = {
                    config.setSelectedReasonFilter(reasonCode)
                    config.setShowFilterMenu(false)
                }
            )
        }
    }
}
