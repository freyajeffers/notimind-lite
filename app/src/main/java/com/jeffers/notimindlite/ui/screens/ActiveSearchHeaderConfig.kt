package com.jeffers.notimindlite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.jeffers.notimindlite.data.local.PreferencesRepository

internal data class ActiveSearchHeaderConfig(
    val searchQuery: String,
    val searchSuggestions: List<String>,
    val recentSearches: List<String>,
    val isVisible: Boolean,
    val focusRequester: FocusRequester,
    val preferences: PreferencesRepository,
    val onQueryChanged: (String) -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val onDone: () -> Unit
)

private data class ActiveSearchDropdownConfig(
    val searchConfig: ActiveSearchHeaderConfig,
    val query: String,
    val suggestions: List<String>,
    val recentSearches: List<String>,
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit
)

internal fun LazyListScope.activeSearchHeader(config: ActiveSearchHeaderConfig) {
    item(key = "active_search_header") {
        activeSearchHeaderContent(config)
    }
}

@Composable
private fun activeSearchHeaderContent(config: ActiveSearchHeaderConfig) {
    var expandedDropdown by remember { mutableStateOf(false) }
    val query = config.searchQuery
    val suggestions = config.searchSuggestions
    val recentSearches = config.recentSearches

    LaunchedEffect(suggestions, recentSearches) {
        expandedDropdown = suggestions.isNotEmpty() || recentSearches.isNotEmpty()
    }
    LaunchedEffect(query) {
        if (query.length >= 2) {
            expandedDropdown = suggestions.isNotEmpty()
        } else if (query.isBlank()) {
            expandedDropdown = recentSearches.isNotEmpty()
        }
    }

    AnimatedVisibility(
        visible = config.isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = config.onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .focusRequester(config.focusRequester)
                    .onFocusChanged { config.onFocusChanged(it.isFocused) }
                    .semantics { contentDescription = "ActiveSearchField" },
                placeholder = { androidx.compose.material3.Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { config.onQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { config.onDone() }),
                shape = androidx.compose.foundation.shape
                    .RoundedCornerShape(12.dp)
            )
            activeSearchDropdown(
                ActiveSearchDropdownConfig(
                    searchConfig = config,
                    query = query,
                    suggestions = suggestions,
                    recentSearches = recentSearches,
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = it }
                )
            )
        }
    }
}

@Composable
private fun activeSearchDropdown(
    config: ActiveSearchDropdownConfig
) {
    val searchConfig = config.searchConfig
    DropdownMenu(
        expanded = config.expanded && (
            config.suggestions.isNotEmpty() ||
                (config.query.isBlank() && config.recentSearches.isNotEmpty())
        ),
        onDismissRequest = { config.onExpandedChange(false) },
        properties = PopupProperties(focusable = false),
        modifier = Modifier.fillMaxWidth(SEARCH_MENU_WIDTH_FRACTION)
    ) {
        val matchingRecent = remember(config.query, config.recentSearches) {
            if (config.query.isBlank()) {
                config.recentSearches
            } else {
                config.recentSearches.filter { it.contains(config.query, ignoreCase = true) }
            }
        }
        matchingRecent.forEach { recent ->
            DropdownMenuItem(
                text = { androidx.compose.material3.Text(recent) },
                trailingIcon = {
                    IconButton(onClick = {
                        searchConfig.preferences.sharedSearchManager.removeRecentSearch(recent)
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove recent search")
                    }
                },
                onClick = {
                    searchConfig.onQueryChanged(recent)
                    config.onExpandedChange(false)
                }
            )
        }
        if (matchingRecent.isNotEmpty()) {
            DropdownMenuItem(
                text = { androidx.compose.material3.Text("Clear recent searches") },
                onClick = {
                    searchConfig.preferences.sharedSearchManager.resetRecentSearches()
                    config.onExpandedChange(false)
                }
            )
        }
        config.suggestions.forEach { suggestion ->
            DropdownMenuItem(
                text = { androidx.compose.material3.Text(suggestion, fontSize = SEARCH_SUGGESTION_FONT_SIZE.sp) },
                onClick = {
                    searchConfig.onQueryChanged(suggestion)
                    config.onExpandedChange(false)
                }
            )
        }
    }
}

private const val SEARCH_SUGGESTION_FONT_SIZE = 14
private const val SEARCH_MENU_WIDTH_FRACTION = 0.9f
