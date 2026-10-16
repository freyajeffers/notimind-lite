package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.ui.components.ActiveFilterChip
import com.jeffers.notimindlite.ui.components.activeFilterChipsRow

internal fun LazyListScope.activeFilterChipsItem(
    searchQuery: String,
    selectedPackages: List<String>?,
    onClearSearch: () -> Unit,
    onClearPackages: () -> Unit,
    onClearAll: () -> Unit
) {
    val chips = mutableListOf<ActiveFilterChip>()
    if (searchQuery.isNotBlank()) {
        chips += ActiveFilterChip(text = "\"$searchQuery\"", onClear = onClearSearch)
    }
    selectedPackages?.takeIf { it.isNotEmpty() }?.let { packages ->
        chips += ActiveFilterChip(
            text = "${packages.size} app${if (packages.size == 1) "" else "s"}",
            onClear = onClearPackages
        )
    }
    if (chips.isNotEmpty()) {
        item(key = "active_filter_chips") {
            activeFilterChipsRow(
                label = stringResource(R.string.active_filter_chips_label),
                chips = chips,
                clearAllLabel = stringResource(R.string.active_filter_chips_clear_all),
                onClearAll = onClearAll
            )
        }
    }
}
