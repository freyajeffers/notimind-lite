package com.jeffers.notimindlite.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.ui.components.ActiveFirstRunEmptyState
import com.jeffers.notimindlite.ui.components.ActivePermissionEmptyState
import com.jeffers.notimindlite.ui.components.ActiveSearchEmptyState

@Composable
internal fun activeEmptyState(
    isGranted: Boolean,
    isSearching: Boolean,
    debouncedSearchQuery: String,
    onGrantClick: () -> Unit,
    onClearClick: () -> Unit
) {
    if (!isGranted) {
        ActivePermissionEmptyState(
            title = stringResource(R.string.active_empty_permission_title),
            description = stringResource(R.string.active_empty_permission_desc),
            step1 = stringResource(R.string.active_empty_permission_step1),
            step2 = stringResource(R.string.active_empty_permission_step2),
            step3 = stringResource(R.string.active_empty_permission_step3),
            grantButtonText = stringResource(R.string.active_empty_permission_grant),
            onGrantClick = onGrantClick
        )
    } else if (isSearching) {
        ActiveSearchEmptyState(
            title = stringResource(R.string.active_empty_search_title),
            description = stringResource(R.string.active_empty_search_desc, debouncedSearchQuery),
            clearButtonText = stringResource(R.string.active_empty_search_clear),
            onClearClick = onClearClick
        )
    } else {
        ActiveFirstRunEmptyState(
            title = stringResource(R.string.active_empty_first_run_title),
            description = stringResource(R.string.active_empty_first_run_desc),
            hint = stringResource(R.string.active_empty_first_run_hint)
        )
    }
}
