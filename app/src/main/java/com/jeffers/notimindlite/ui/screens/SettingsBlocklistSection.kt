package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.ui.dialogs.AppPackageOptionRow

private const val LIST_MAX_HEIGHT_DP = 300

/**
 * Blocklist entry point.
 *
 * Typing comma-separated package names asked a user to know Android package identifiers,
 * which is not something they can reasonably do, and one typo silently changed what gets
 * blocked. Pick from the apps NotiMind has actually seen instead.
 */
@Composable
internal fun PackageBlocklistRow(
    blockedCount: Int,
    onConfigureClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_capture_blocklist_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (blockedCount == 0) {
                    stringResource(R.string.pref_capture_blocklist_none)
                } else {
                    stringResource(R.string.pref_capture_blocklist_count, blockedCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onConfigureClick) {
            Text(stringResource(R.string.pref_capture_blocklist_choose))
        }
    }
}

@Composable
internal fun PackageBlocklistDialog(
    selectedPackages: List<String>,
    availableApps: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentSelected = remember { mutableStateListOf<String>().apply { addAll(selectedPackages) } }
    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isBlank()) {
            availableApps
        } else {
            availableApps.filter {
                it.second.contains(searchQuery, ignoreCase = true) ||
                    it.first.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_capture_blocklist_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (availableApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pref_capture_blocklist_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.package_filter_search_label)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
                        },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT_DP.dp)) {
                        items(filteredApps, key = { it.first }) { (pkg, label) ->
                            AppPackageOptionRow(
                                packageName = pkg,
                                label = label,
                                checked = currentSelected.contains(pkg),
                                onCheckedChange = { checked ->
                                    if (checked) currentSelected.add(pkg) else currentSelected.remove(pkg)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(currentSelected.toList()) }) {
                Text(stringResource(R.string.pref_capture_blocklist_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
