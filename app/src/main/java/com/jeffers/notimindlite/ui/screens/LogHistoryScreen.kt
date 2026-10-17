package com.jeffers.notimindlite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import com.jeffers.notimindlite.ui.components.NotificationGroupCard
import com.jeffers.notimindlite.ui.components.UiNotificationGroup
import com.jeffers.notimindlite.ui.components.groupNotifications
import org.json.JSONArray
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.jeffers.notimindlite.R
import androidx.compose.foundation.background
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.dialogs.appPackageSelectorDialog
import com.jeffers.notimindlite.ui.components.notificationDetailPanel
import com.jeffers.notimindlite.domain.search.HybridSearchEngine
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.util.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

private const val PREFETCH_AHEAD = 24
private const val PREFETCH_BEHIND = 2
private const val BASE_SORT_MODE_COUNT = 3
private const val SEARCH_DEBOUNCE_MILLIS = 100L

private const val MAX_RECENT_SEARCHES = 9
private const val SEARCH_FIELD_CORNER_RADIUS = 12


enum class SortMode(val label: String) {
    DISMISSED("Time Dismissed"),
    RECEIVED("Time Received"),
    ALL("All Notifications"),
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    APP_NAME("App Name"),
    TITLE("Title")
}

private fun sortModeFromPreference(pref: String): SortMode = when (pref) {
    "newest" -> SortMode.NEWEST
    "oldest" -> SortMode.OLDEST
    "app" -> SortMode.APP_NAME
    "title" -> SortMode.TITLE
    "dismissed" -> SortMode.DISMISSED
    "received" -> SortMode.RECEIVED
    "all" -> SortMode.ALL
    else -> SortMode.DISMISSED
}

private fun sortModePreference(mode: SortMode): String = when (mode) {
    SortMode.NEWEST -> "newest"
    SortMode.OLDEST -> "oldest"
    SortMode.APP_NAME -> "app"
    SortMode.TITLE -> "title"
    SortMode.DISMISSED -> "dismissed"
    SortMode.RECEIVED -> "received"
    SortMode.ALL -> "all"
}

data class LogHistoryCardState(
    val item: NotificationEntity,
    val dateTimeFormatter: DateTimeFormatter,
    val dao: NotificationDao,
    val isExpanded: Boolean,
    val highlightQuery: String = "",
    val onToggleExpand: () -> Unit
)

data class LogHistorySearchState(
    val searchQuery: String,
    val onSearchChange: (String) -> Unit,
    val onDone: (String) -> Unit,
    val onClear: () -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    val modifier: Modifier = Modifier
)

@Composable
private fun logHistorySearchField(state: LogHistorySearchState) {
    val searchQuery = state.searchQuery
    val onSearchChange = state.onSearchChange
    val onDone = state.onDone
    val onClear = state.onClear
    val onFocusChanged = state.onFocusChanged
    val keyboardController = state.keyboardController
    val modifier = state.modifier
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) },
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Search")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onDone(searchQuery)
            keyboardController?.hide()
        }),
        shape = RoundedCornerShape(SEARCH_FIELD_CORNER_RADIUS.dp)
    )
}

private fun filterAndSortNotifications(
    activeList: List<NotificationEntity>,
    reason: Int?,
    packages: List<String>?,
    query: String,
    sortMode: SortMode
): List<NotificationEntity> {
    var list = activeList.distinctBy { "${it.packageName}_${it.title}_${it.content}" }
    if (reason != null) list = list.filter { it.dismissReason == reason }
    if (!packages.isNullOrEmpty()) list = list.filter { packages.contains(it.packageName) }
    if (query.isNotBlank()) list = HybridSearchEngine.searchAndRankBlocking(list, query)
    return when (sortMode) {
        SortMode.NEWEST -> list.sortedByDescending { it.postTime }
        SortMode.OLDEST -> list.sortedBy { it.postTime }
        SortMode.APP_NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
        SortMode.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        else -> list
    }
}

@Composable
private fun notificationIconPrefetch(
    context: android.content.Context,
    listState: androidx.compose.foundation.lazy.LazyListState,
    notificationGroups: List<UiNotificationGroup>
) {
    LaunchedEffect(notificationGroups, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visibleIndexes ->
                if (visibleIndexes.isEmpty() || notificationGroups.isEmpty()) return@collect
                val first = ((visibleIndexes.minOrNull() ?: 0) - PREFETCH_BEHIND).coerceAtLeast(0)
                val last = ((visibleIndexes.maxOrNull() ?: 0) + PREFETCH_AHEAD)
                    .coerceAtMost(notificationGroups.lastIndex)
                val candidates = notificationGroups
                    .subList(first, last + 1)
                    .flatMap { it.items }
                    .mapNotNull { it.appIconUri }
                    .distinct()
                withContext(Dispatchers.IO) {
                    candidates.map { uri -> async { AppIconCache.getIcon(context, uri) } }.awaitAll()
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun logHistoryScreen(dao: NotificationDao, preferences: com.jeffers.notimindlite.data.local.PreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // F-K fix: persist user-meaningful state across process death / rotation.
    // Transient UI state (showSortMenu etc.) stays on `remember` — only durable
    // user input (sort/filter/search) survives.
    // Initialize sortMode from persisted preference when available; keep UI-local default for direct interactions.
    val prefSort by preferences.displayManager.sortOrder.collectAsState()
    var sortMode by remember(prefSort) { mutableStateOf(sortModeFromPreference(prefSort)) }
    LaunchedEffect(sortMode) {
        preferences.displayManager.setSortOrder(sortModePreference(sortMode))
    }
    var selectedReasonFilter by rememberSaveable { mutableStateOf(preferences.sharedSearchManager.reason.value) }
    var selectedPackages by remember { mutableStateOf(preferences.sharedSearchManager.packages.value) }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showPackagePicker by remember { mutableStateOf(false) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }

    val allNotifsDismissed by dao.getDismissedNotificationsSortedByDismissed().collectAsState(initial = emptyList())
    val allNotifsReceived by dao.getDismissedNotificationsSortedByReceived().collectAsState(initial = emptyList())
    val allNotifsEver by dao.getAllNotificationsSortedByDismissed().collectAsState(initial = emptyList())

    val activeList = when (sortMode) {
        SortMode.DISMISSED -> allNotifsDismissed
        SortMode.RECEIVED -> allNotifsReceived
        SortMode.ALL, SortMode.NEWEST, SortMode.OLDEST, SortMode.APP_NAME, SortMode.TITLE -> allNotifsEver
    }
    // F-K fix: persist search text across process death.
    var searchQuery by rememberSaveable { mutableStateOf(preferences.sharedSearchManager.query.value) }
    // debouncedSearchQuery is a derived value, not user input; do not save.
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var recentSearches by rememberSaveable { mutableStateOf(preferences.sharedSearchManager.recentSearches.value) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    logHistorySearchLifecycle(
        LogHistorySearchLifecycleConfig(
            searchQuery = searchQuery,
            selectedPackages = selectedPackages,
            selectedReasonFilter = selectedReasonFilter,
            isSearchFocused = isSearchFocused,
            preferences = preferences,
            setDebouncedSearchQuery = { debouncedSearchQuery = it },
            setIsSearchVisible = { isSearchVisible = it }
        )
    )

    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }

    val availableReasons = remember(activeList) {
        activeList.mapNotNull { it.dismissReason }.distinct().sorted()
    }

    val availableApps = remember(activeList) {
        activeList.map { it.packageName to it.appName }.distinctBy { it.first }
    }

    val filteredNotifs by remember(activeList, selectedReasonFilter, selectedPackages, debouncedSearchQuery, sortMode) {
        derivedStateOf {
            filterAndSortNotifications(
                activeList,
                selectedReasonFilter,
                selectedPackages,
                debouncedSearchQuery,
                sortMode
            )
        }
    }

    val notificationGroups = remember(filteredNotifs) {
        groupNotifications(filteredNotifs)
    }

    notificationIconPrefetch(context, listState, notificationGroups)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                        logHistoryTopActions(
                        LogHistoryTopActions(
                            selectedPackages = selectedPackages,
                            selectedReasonFilter = selectedReasonFilter,
                            searchQuery = searchQuery,
                            isSearchFocused = isSearchFocused,
                            isSearchVisible = isSearchVisible,
                            focusManager = focusManager,
                            keyboardController = keyboardController,
                            availableReasons = availableReasons,
                            availableApps = availableApps,
                            showFilterMenu = showFilterMenu,
                            setShowFilterMenu = { showFilterMenu = it },
                            showPackagePicker = showPackagePicker,
                            setShowPackagePicker = { showPackagePicker = it },
                            setSelectedPackages = { selectedPackages = it },
                            setSelectedReasonFilter = { selectedReasonFilter = it },
                            preferences = preferences
                        )
                    )

                    Box {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = { PlainTooltip { Text("Sort Logs") } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Log History")
                            }
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.log_history_sort_dismissed,
                                            if (sortMode == SortMode.DISMISSED) "✓" else ""
                                        )
                                    )
                                },
                                onClick = {
                                    sortMode = SortMode.DISMISSED
                                    preferences.displayManager.setSortOrder(sortModePreference(sortMode))
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.log_history_sort_received,
                                            if (sortMode == SortMode.RECEIVED) "✓" else ""
                                        )
                                    )
                                },
                                onClick = {
                                    sortMode = SortMode.RECEIVED
                                    preferences.displayManager.setSortOrder(sortModePreference(sortMode))
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.log_history_sort_all,
                                            if (sortMode == SortMode.ALL) "✓" else ""
                                        )
                                    )
                                },
                                onClick = {
                                    sortMode = SortMode.ALL
                                    preferences.displayManager.setSortOrder(sortModePreference(sortMode))
                                    showSortMenu = false
                                }
                            )
                            SortMode.entries.drop(BASE_SORT_MODE_COUNT).forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.label} ${if (sortMode == option) "✓" else ""}") },
                                    onClick = {
                                        sortMode = option
                                        preferences.displayManager.setSortOrder(sortModePreference(sortMode))
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { isSearchVisible = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            logHistoryScrollActions(filteredNotifs.size, listState, scope)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val searchSuggestions = remember(searchQuery, activeList) {
                logHistorySearchSuggestions(searchQuery, activeList)
            }
            var expandedDropdown by remember { mutableStateOf(false) }

            LaunchedEffect(searchSuggestions, recentSearches) {
                expandedDropdown = searchSuggestions.isNotEmpty() || recentSearches.isNotEmpty()
            }

            if (isSearchVisible || searchQuery.isNotBlank()) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                logHistorySearchField(
                    LogHistorySearchState(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onDone = { rawQuery ->
                        val query = rawQuery.trim()
                        if (query.isNotEmpty()) {
                            recentSearches = listOf(query) + recentSearches
                                .filterNot { it == query }
                                .take(MAX_RECENT_SEARCHES)
                            preferences.sharedSearchManager.addRecentSearch(query)
                        }
                        focusManager.clearFocus(force = true)
                    },
                    onClear = { searchQuery = "" },
                    onFocusChanged = { isSearchFocused = it },
                    keyboardController = keyboardController,
                    modifier = Modifier.fillMaxWidth()
                    )
                )

                logHistorySearchDropdown(
                    LogHistorySearchDropdownConfig(
                        expanded = expandedDropdown,
                        searchSuggestions = searchSuggestions,
                        recentSearches = recentSearches,
                        searchQuery = searchQuery,
                        onRemoveRecent = { preferences.sharedSearchManager.removeRecentSearch(it) },
                        onClearRecent = { preferences.sharedSearchManager.resetRecentSearches() },
                        onSelectSuggestion = { v -> searchQuery = v },
                        onDismiss = { expandedDropdown = false }
                    )
                )
            }

            renderLogHistoryResults(
                LogHistoryResultsConfig(
                    filteredNotifs = filteredNotifs,
                    searchQuery = searchQuery,
                    selectedReasonFilter = selectedReasonFilter,
                    selectedPackages = selectedPackages,
                    setSearchQuery = { searchQuery = it },
                    setSelectedReasonFilter = { selectedReasonFilter = it },
                    setSelectedPackages = { selectedPackages = it },
                    listState = listState,
                    notificationGroups = notificationGroups,
                    collapsedGroups = collapsedGroups,
                    setCollapsedGroups = { collapsedGroups = it },
                    expandedCards = expandedCards,
                    setExpandedCards = { expandedCards = it },
                    debouncedSearchQuery = debouncedSearchQuery,
                    dateTimeFormatter = dateTimeFormatter,
                    dao = dao
                )
            )
            renderLogHistoryPackagePicker(
                showPackagePicker,
                selectedPackages,
                availableApps,
                { showPackagePicker = it },
                { selectedPackages = it }
            )
        }
    }
}

private val renderLogHistoryPackagePicker:
    @Composable (
        Boolean,
        List<String>?,
        List<Pair<String, String>>,
        (Boolean) -> Unit,
        (List<String>?) -> Unit
    ) -> Unit =
    { visible, selectedPackages, availableApps, setVisible, setPackages ->
        if (visible) {
            appPackageSelectorDialog(
                selectedPackages = selectedPackages ?: emptyList(),
                availableApps = availableApps,
                onDismiss = { setVisible(false) },
                onPackagesSelected = {
                    setPackages(it)
                    setVisible(false)
                }
            )
        }
    }

private data class LogHistoryResultsConfig(
    val filteredNotifs: List<NotificationEntity>,
    val searchQuery: String,
    val selectedReasonFilter: Int?,
    val selectedPackages: List<String>?,
    val setSearchQuery: (String) -> Unit,
    val setSelectedReasonFilter: (Int?) -> Unit,
    val setSelectedPackages: (List<String>?) -> Unit,
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val notificationGroups: List<UiNotificationGroup>,
    val collapsedGroups: Set<String>,
    val setCollapsedGroups: (Set<String>) -> Unit,
    val expandedCards: Set<String>,
    val setExpandedCards: (Set<String>) -> Unit,
    val debouncedSearchQuery: String,
    val dateTimeFormatter: DateTimeFormatter,
    val dao: NotificationDao
)

private val renderLogHistoryResults:
    @Composable (LogHistoryResultsConfig) -> Unit = { config ->
    if (config.filteredNotifs.isEmpty()) {
        com.jeffers.notimindlite.ui.components.ActiveSearchEmptyState(
            title = if (config.searchQuery.isBlank() &&
                config.selectedReasonFilter == null && config.selectedPackages == null
            ) stringResource(R.string.log_history_empty_initial)
            else stringResource(R.string.log_history_empty_search),
            description = "No notifications match your current filters.",
            clearButtonText = "Clear All Filters",
            onClearClick = {
                config.setSearchQuery("")
                config.setSelectedReasonFilter(null)
                config.setSelectedPackages(null)
            }
        )
    } else {
        renderLogHistoryList(
            LogHistoryListConfig(
                listState = config.listState,
                groups = config.notificationGroups,
                collapsedGroups = config.collapsedGroups,
                setCollapsedGroups = config.setCollapsedGroups,
                expandedCards = config.expandedCards,
                setExpandedCards = config.setExpandedCards,
                debouncedSearchQuery = config.debouncedSearchQuery,
                dateTimeFormatter = config.dateTimeFormatter,
                dao = config.dao
            )
        )
    }
}

private data class LogHistorySearchLifecycleConfig(
    val searchQuery: String,
    val selectedPackages: List<String>?,
    val selectedReasonFilter: Int?,
    val isSearchFocused: Boolean,
    val preferences: com.jeffers.notimindlite.data.local.PreferencesRepository,
    val setDebouncedSearchQuery: (String) -> Unit,
    val setIsSearchVisible: (Boolean) -> Unit
)

private val logHistorySearchLifecycle:
    @Composable (LogHistorySearchLifecycleConfig) -> Unit = { config ->
    LaunchedEffect(config.searchQuery) {
        config.preferences.sharedSearchManager.setQuery(config.searchQuery)
        delay(SEARCH_DEBOUNCE_MILLIS)
        config.setDebouncedSearchQuery(config.searchQuery)
    }
    LaunchedEffect(config.selectedPackages) {
        config.preferences.sharedSearchManager.setPackages(config.selectedPackages)
    }
    LaunchedEffect(config.selectedReasonFilter) {
        config.preferences.sharedSearchManager.setReason(config.selectedReasonFilter)
    }
    LaunchedEffect(config.isSearchFocused, config.searchQuery) {
        if (!config.isSearchFocused && config.searchQuery.isBlank()) {
            config.setIsSearchVisible(false)
        }
    }
}

private data class LogHistoryListConfig(
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val groups: List<UiNotificationGroup>,
    val collapsedGroups: Set<String>,
    val setCollapsedGroups: (Set<String>) -> Unit,
    val expandedCards: Set<String>,
    val setExpandedCards: (Set<String>) -> Unit,
    val debouncedSearchQuery: String,
    val dateTimeFormatter: DateTimeFormatter,
    val dao: NotificationDao
)

private val renderLogHistoryList: @Composable (LogHistoryListConfig) -> Unit = { config ->
    LazyColumn(
        state = config.listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = config.groups,
            key = { group -> "history_group_${group.groupKey}" },
            contentType = { group -> if (group.items.size == 1) "notification" else "group" }
        ) { group ->
            val isGroupExpanded = !config.collapsedGroups.contains(group.groupKey)
            if (group.items.size == 1) {
                val item = group.items[0]
                logHistoryCard(
                    LogHistoryCardState(
                        item = item,
                        dateTimeFormatter = config.dateTimeFormatter,
                        dao = config.dao,
                        isExpanded = config.expandedCards.contains(item.key),
                        highlightQuery = config.debouncedSearchQuery,
                        onToggleExpand = {
                            config.setExpandedCards(
                                if (config.expandedCards.contains(item.key)) {
                                    config.expandedCards - item.key
                                } else {
                                    config.expandedCards + item.key
                                }
                            )
                        }
                    )
                )
            } else {
                NotificationGroupCard(
                    group = group,
                    dateTimeFormatter = config.dateTimeFormatter,
                    dao = config.dao,
                    isGroupExpanded = isGroupExpanded,
                    onToggleGroupExpand = {
                        config.setCollapsedGroups(
                            if (config.collapsedGroups.contains(group.groupKey)) {
                                config.collapsedGroups - group.groupKey
                            } else {
                                config.collapsedGroups + group.groupKey
                            }
                        )
                    },
                    renderChildCard = { childItem ->
                        logHistoryCard(
                            LogHistoryCardState(
                                item = childItem,
                                dateTimeFormatter = config.dateTimeFormatter,
                                dao = config.dao,
                                isExpanded = config.expandedCards.contains(childItem.key),
                                highlightQuery = config.debouncedSearchQuery,
                                onToggleExpand = {
                                    config.setExpandedCards(
                                        if (config.expandedCards.contains(childItem.key)) {
                                            config.expandedCards - childItem.key
                                        } else {
                                            config.expandedCards + childItem.key
                                        }
                                    )
                                }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun logHistoryScrollActions(
    itemCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (itemCount == 0) return
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        logHistoryScrollButton(
            icon = Icons.Default.KeyboardArrowUp,
            description = "Scroll to Top",
            onClick = { scope.launch { listState.animateScrollToItem(0) } }
        )
        logHistoryScrollButton(
            icon = Icons.Default.KeyboardArrowDown,
            description = "Scroll to Bottom",
            onClick = { scope.launch { listState.animateScrollToItem(itemCount - 1) } }
        )
    }
}

@Composable
private fun logHistoryScrollButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = description)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun dismissStatusBadge(item: NotificationEntity) {
    if (item.isDismissed && item.dismissReason != null) {
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                text = stringResource(id = getReasonLabel(item.dismissReason)),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    } else if (!item.isDismissed) {
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text(
                text = stringResource(R.string.notification_active_filter),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun logHistoryCard(state: LogHistoryCardState) {
    val item = state.item
    val dateTimeFormatter = state.dateTimeFormatter
    val dao = state.dao
    val isExpanded = state.isExpanded
    val highlightQuery = state.highlightQuery
    val onToggleExpand = state.onToggleExpand
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            logHistoryCardHeader(state)

            if (isExpanded) {
                logHistoryExpandedContent(
                    item = item,
                    dateTimeFormatter = dateTimeFormatter,
                    highlightQuery = highlightQuery
                )
            }
        }
    }
}
