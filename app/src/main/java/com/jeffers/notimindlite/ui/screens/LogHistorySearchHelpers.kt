package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.data.local.NotificationEntity

private const val MIN_SEARCH_LEN = 2
private const val MAX_SUGGESTIONS = 4
private const val MENU_WIDTH_FRACTION = 0.9f

data class LogHistorySearchDropdownConfig(
    val expanded: Boolean,
    val searchSuggestions: List<String>,
    val recentSearches: List<String>,
    val searchQuery: String,
    val onRemoveRecent: (String) -> Unit,
    val onClearRecent: () -> Unit,
    val onSelectSuggestion: (String) -> Unit,
    val onDismiss: () -> Unit
)

fun logHistorySearchSuggestions(query: String, activeList: List<NotificationEntity>): List<String> {
    if (query.length < MIN_SEARCH_LEN) return emptyList()
    return (activeList.map { it.appName } + activeList.map { it.title })
        .filter { it.contains(query, ignoreCase = true) }
        .distinct()
        .take(MAX_SUGGESTIONS)
}

@Composable
fun logHistorySearchDropdown(
    config: LogHistorySearchDropdownConfig
) {
    val expanded = config.expanded
    val searchSuggestions = config.searchSuggestions
    val recentSearches = config.recentSearches
    val searchQuery = config.searchQuery
    DropdownMenu(
        expanded = expanded && (
            searchSuggestions.isNotEmpty() ||
                (searchQuery.isBlank() && recentSearches.isNotEmpty())
        ),
        onDismissRequest = config.onDismiss,
        properties = androidx.compose.ui.window
            .PopupProperties(focusable = false),
        modifier = Modifier.fillMaxWidth(MENU_WIDTH_FRACTION)
    ) {
        val matchingRecent = if (searchQuery.isBlank()) {
            recentSearches
        } else {
            recentSearches.filter { it.contains(searchQuery, ignoreCase = true) }
        }
        matchingRecent.forEach { recent ->
            DropdownMenuItem(
                text = { Text(recent) },
                trailingIcon = {
                    IconButton(onClick = { config.onRemoveRecent(recent) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove recent search")
                    }
                },
                onClick = {
                    config.onSelectSuggestion(recent)
                    config.onDismiss()
                }
            )
        }
        if (matchingRecent.isNotEmpty()) {
            DropdownMenuItem(
                text = { Text("Clear recent searches") },
                onClick = {
                    config.onClearRecent()
                    config.onDismiss()
                }
            )
        }
        searchSuggestions.forEach { suggestion ->
            DropdownMenuItem(
                text = { Text(suggestion, fontSize = 14.sp) },
                onClick = {
                    config.onSelectSuggestion(suggestion)
                    config.onDismiss()
                }
            )
        }
    }
}
