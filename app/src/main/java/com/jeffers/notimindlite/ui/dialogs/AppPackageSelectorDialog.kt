package com.jeffers.notimindlite.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun appPackageSelectorDialog(
    selectedPackages: List<String>,
    availableApps: List<Pair<String, String>>, // (packageName, appName)
    onDismiss: () -> Unit,
    onPackagesSelected: (List<String>?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentSelected = remember { mutableStateListOf<String>().apply { addAll(selectedPackages) } }

    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isBlank()) availableApps
        else availableApps.filter {
            it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Application") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search apps") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(filteredApps, key = { it.first }) { (pkg, label) ->
                        val isChecked = currentSelected.contains(pkg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) currentSelected.remove(pkg)
                                    else currentSelected.add(pkg)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) currentSelected.add(pkg) else currentSelected.remove(pkg)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPackagesSelected(if (currentSelected.isEmpty()) null else currentSelected.toList()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = { onPackagesSelected(null) }) {
                Text("Clear All")
            }
        }
    )
}
