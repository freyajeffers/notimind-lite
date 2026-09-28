package com.jeffers.notimindlite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import com.jeffers.notimindlite.ui.components.NotificationGroupCard
import com.jeffers.notimindlite.ui.components.groupNotifications
import org.json.JSONArray
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.annotation.StringRes
import com.jeffers.notimindlite.R
import androidx.compose.foundation.background
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.dialogs.AppPackageSelectorDialog
import com.jeffers.notimindlite.ui.components.NotificationDetailPanel
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
import java.util.*

private const val PREFETCH_AHEAD = 24
private const val PREFETCH_BEHIND = 2
private const val BASE_SORT_MODE_COUNT = 3

enum class SortMode(@StringRes val labelRes: Int) {
    DISMISSED(R.string.log_history_sort_dismissed),
    RECEIVED(R.string.log_history_sort_received),
    ALL(R.string.log_history_sort_all),
    NEWEST(R.string.log_history_sort_newest),
    OLDEST(R.string.log_history_sort_oldest),
    APP_NAME(R.string.log_history_sort_app),
    TITLE(R.string.log_history_sort_title)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogHistoryScreen(dao: NotificationDao, authManager: AuthManager, db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // F-K fix: persist user-meaningful state across process death / rotation.
    // Transient UI state (showSortMenu etc.) stays on `remember` — only durable
    // user input (sort/filter/search) survives.
    var sortMode by rememberSaveable { mutableStateOf(SortMode.DISMISSED) }
    var selectedReasonFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedPackages by rememberSaveable { mutableStateOf<List<String>?>(null) }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showPackagePicker by remember { mutableStateOf(false) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }

    val allNotifsDismissed by dao.getDismissedNotificationsSortedByDismissed().collectAsState(initial = emptyList())
    val allNotifsReceived by dao.getDismissedNotificationsSortedByReceived().collectAsState(initial = emptyList())
    val allNotifsEver by dao.getAllNotificationsSortedByDismissed().collectAsState(initial = emptyList())
    val totalCount by dao.getTotalNotificationCountFlow().collectAsState(initial = 0)

    val activeList = when (sortMode) {
        SortMode.DISMISSED -> allNotifsDismissed
        SortMode.RECEIVED -> allNotifsReceived
        SortMode.ALL, SortMode.NEWEST, SortMode.OLDEST, SortMode.APP_NAME, SortMode.TITLE -> allNotifsEver
    }
    // F-K fix: persist search text across process death.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // debouncedSearchQuery is a derived value, not user input; do not save.
    var debouncedSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(100L)
        debouncedSearchQuery = searchQuery
    }

    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss", Locale.getDefault()).withZone(ZoneId.systemDefault())
    }

    val availableReasons = remember(activeList) {
        activeList.mapNotNull { it.dismissReason }.distinct().sorted()
    }

    val availableApps = remember(activeList) {
        activeList.map { it.packageName to it.appName }.distinctBy { it.first }
    }

    val selectedPackageSet = remember(selectedPackages) { selectedPackages?.toSet().orEmpty() }
    val filteredNotifs = remember(
        activeList,
        selectedReasonFilter,
        selectedPackageSet,
        debouncedSearchQuery,
        sortMode
    ) {
        var list = activeList.distinctBy { "${it.packageName}_${it.title}_${it.content}" }

        if (selectedReasonFilter != null) {
            list = list.filter { it.dismissReason == selectedReasonFilter }
        }

        if (selectedPackageSet.isNotEmpty()) {
            list = list.filter { it.packageName in selectedPackageSet }
        }

        if (debouncedSearchQuery.isNotBlank()) {
            list = HybridSearchEngine.searchAndRankBlocking(list, debouncedSearchQuery)
        }

        when (sortMode) {
            SortMode.NEWEST -> list.sortedByDescending { it.postTime }
            SortMode.OLDEST -> list.sortedBy { it.postTime }
            SortMode.APP_NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
            SortMode.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            else -> list
        }
    }

    val notificationGroups = remember(filteredNotifs) {
        groupNotifications(filteredNotifs)
    }

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
                    candidates.distinct().map { uri -> async { AppIconCache.getIcon(context, uri) } }.awaitAll()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.log_history_title, filteredNotifs.size, totalCount),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        val hasActiveFilters = !selectedPackages.isNullOrEmpty() || selectedReasonFilter != null
                        TooltipBox(
                            positionProvider = TooltipDefaults
                                .rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(stringResource(id = R.string.log_history_filter_title)) } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = stringResource(id = R.string.log_history_filter_title),
                                    tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.log_history_filter_app_count, selectedPackages?.size?.toString() ?: "All")) },
                                onClick = {
                                    showFilterMenu = false
                                    showPackagePicker = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.log_history_filter_all_reasons, if (selectedReasonFilter == null) "✓" else "")) },
                                onClick = {
                                    selectedReasonFilter = null
                                    showFilterMenu = false
                                }
                            )
                            availableReasons.forEach { reasonCode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.log_history_filter_reason_item, getReasonLabel(reasonCode), reasonCode, if (selectedReasonFilter == reasonCode) "✓" else "")) },
                                    onClick = {
                                        selectedReasonFilter = reasonCode
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        TooltipBox(
                            positionProvider = TooltipDefaults
                                .rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.log_history_sort_title)) } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.notification_action_sort_history)
                                )
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
                                    showSortMenu = false
                                }
                            )
                            SortMode.entries.drop(BASE_SORT_MODE_COUNT).forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(option.labelRes) +
                                                if (sortMode == option) " ✓" else ""
                                        )
                                    },
                                    onClick = {
                                        sortMode = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (filteredNotifs.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.common_scroll_top)) } },
                        state = rememberTooltipState()
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.notification_action_scroll_top)
                            )
                        }
                    }

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.common_scroll_bottom)) } },
                        state = rememberTooltipState()
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(notificationGroups.lastIndex.coerceAtLeast(0))
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.notification_action_scroll_bottom)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val searchSuggestions = remember(searchQuery, activeList) {
                if (searchQuery.length < 2) emptyList()
                else {
                    (activeList.map { it.appName } + activeList.map { it.title })
                        .filter { it.contains(searchQuery, ignoreCase = true) }
                        .distinct()
                        .take(4)
                }
            }
            var expandedDropdown by remember { mutableStateOf(false) }

            LaunchedEffect(searchSuggestions) {
                expandedDropdown = searchSuggestions.isNotEmpty()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.common_search)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.common_search)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                DropdownMenu(
                    expanded = expandedDropdown && searchSuggestions.isNotEmpty(),
                    onDismissRequest = { expandedDropdown = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    searchSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion, fontSize = 14.sp) },
                            onClick = {
                                searchQuery = suggestion
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            if (filteredNotifs.isEmpty()) {
                com.jeffers.notimindlite.ui.components.ActiveSearchEmptyState(
                    title = if (searchQuery.isBlank() && selectedReasonFilter == null && selectedPackages == null)
                        stringResource(R.string.log_history_empty_initial)
                    else
                        stringResource(R.string.log_history_empty_search),
                    description = stringResource(R.string.log_history_empty_filter_desc),
                    clearButtonText = stringResource(R.string.log_history_clear_filters),
                    onClearClick = {
                        searchQuery = ""
                        selectedReasonFilter = null
                        selectedPackages = null
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                        .align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = notificationGroups,
                        key = { group -> "history_group_${group.groupKey}" },
                        contentType = { group -> if (group.items.size == 1) "notification" else "group" }
                    ) { group ->
                        val isGroupExpanded = !collapsedGroups.contains(group.groupKey)
                        if (group.items.size == 1) {
                            val item = group.items[0]
                            val cardExpanded = expandedCards.contains(item.key)
                            LogHistoryCard(
                                item = item,
                                dateTimeFormatter = dateTimeFormatter,
                                dao = dao,
                                isExpanded = cardExpanded,
                                onToggleExpand = {
                                    expandedCards = if (cardExpanded) {
                                        expandedCards - item.key
                                    } else {
                                        expandedCards + item.key
                                    }
                                }
                            )
                        } else {
                            NotificationGroupCard(
                                group = group,
                                dateTimeFormatter = dateTimeFormatter,
                                dao = dao,
                                isGroupExpanded = isGroupExpanded,
                                onToggleGroupExpand = {
                                    collapsedGroups = if (collapsedGroups.contains(group.groupKey)) {
                                        collapsedGroups - group.groupKey
                                    } else {
                                        collapsedGroups + group.groupKey
                                    }
                                },
                                renderChildCard = { childItem ->
                                    val cardExpanded = expandedCards.contains(childItem.key)
                                    LogHistoryCard(
                                        item = childItem,
                                        dateTimeFormatter = dateTimeFormatter,
                                        dao = dao,
                                        isExpanded = cardExpanded,
                                        onToggleExpand = {
                                            expandedCards = if (cardExpanded) {
                                                expandedCards - childItem.key
                                            } else {
                                                expandedCards + childItem.key
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            if (showPackagePicker) {
                AppPackageSelectorDialog(
                    selectedPackages = selectedPackages ?: emptyList(),
                    availableApps = availableApps,
                    onDismiss = { showPackagePicker = false },
                    onPackagesSelected = { pkgs ->
                        selectedPackages = pkgs
                        showPackagePicker = false
                    }
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissStatusBadge(item: NotificationEntity) {
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
                text = "Active",
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
@Suppress("CyclomaticComplexMethod", "LongMethod", "FunctionNaming")
fun LogHistoryCard(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter,
    dao: NotificationDao,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconImage(appIconUri = item.appIconUri)
                    if (!item.appIconUri.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(
                            stringResource(
                                if (item.isPinned) {
                                    R.string.log_history_unpin_tooltip
                                } else {
                                    R.string.log_history_pin_tooltip
                                }
                            )
                        ) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    dao.updatePinnedStatus(item.key, !item.isPinned)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (item.isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (item.isPinned) {
                                        R.string.log_history_unpin_desc
                                    } else {
                                        R.string.log_history_pin_desc
                                    }
                                ),
                                tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DismissStatusBadge(item)

                    Spacer(modifier = Modifier.width(6.dp))
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Open notification") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                NotificationLauncher.launchNotification(
                                    context,
                                    item.packageName,
                                    item.key,
                                    item.intentUri
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.log_history_open_desc)
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.subText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    if (!item.bigText.isNullOrEmpty() && item.bigText != item.content) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.bigText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                    @Suppress("SwallowedException")
                    val inboxLines = remember(item.inboxLinesJson) {
                        if (item.inboxLinesJson.isNullOrBlank()) {
                            emptyList()
                        } else {
                            try {
                                val array = JSONArray(item.inboxLinesJson)
                                (0 until array.length()).mapNotNull { idx ->
                                    val str = array.optString(idx)
                                    str.takeIf { it.isNotBlank() }
                                }
                            } catch (_: org.json.JSONException) {
                                emptyList()
                            }
                        }
                    }
                    if (inboxLines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Inbox Lines (${inboxLines.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                inboxLines.forEach { line ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "• ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NotificationExpandedAttributes(
                        item = item,
                        dateTimeFormatter = dateTimeFormatter
                    )
                }
            }
        }
    }
}
