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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.jeffers.notimindlite.R
import androidx.compose.foundation.background
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.dialogs.AppPackageSelectorDialog
import com.jeffers.notimindlite.ui.components.NotificationDetailPanel
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

enum class SortMode(val label: String) {
    DISMISSED("Time Dismissed"),
    RECEIVED("Time Received")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogHistoryScreen(dao: NotificationDao, authManager: AuthManager, db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var sortMode by remember { mutableStateOf(SortMode.DISMISSED) }
    var selectedReasonFilter by remember { mutableStateOf<Int?>(null) }
    var selectedPackages by remember { mutableStateOf<List<String>?>(null) }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showPackagePicker by remember { mutableStateOf(false) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }

    val allNotifsDismissed by dao.getAllNotificationsSortedByDismissed().collectAsState(initial = emptyList())
    val allNotifsReceived by dao.getAllNotificationsSortedByReceived().collectAsState(initial = emptyList())
    val totalCount by dao.getTotalNotificationCountFlow().collectAsState(initial = 0)

    val activeList = if (sortMode == SortMode.DISMISSED) allNotifsDismissed else allNotifsReceived
    var searchQuery by remember { mutableStateOf("") }
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

    val filteredNotifs by remember {
        derivedStateOf {
            var list = activeList.distinctBy { "${it.packageName}_${it.title}_${it.content}" }

            if (selectedReasonFilter != null) {
                list = list.filter { it.dismissReason == selectedReasonFilter }
            }

            if (!selectedPackages.isNullOrEmpty()) {
                list = list.filter { selectedPackages!!.contains(it.packageName) }
            }

            if (debouncedSearchQuery.isBlank()) {
                list
            } else {
                list.filter { it.title.contains(debouncedSearchQuery, ignoreCase = true) || it.content.contains(debouncedSearchQuery, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.log_history_title, filteredNotifs.size, totalCount), fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        val hasActiveFilters = !selectedPackages.isNullOrEmpty() || selectedReasonFilter != null
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
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
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
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
                                text = { Text("Sort by Time Dismissed ${if (sortMode == SortMode.DISMISSED) "✓" else ""}") },
                                onClick = {
                                    sortMode = SortMode.DISMISSED
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Time Received ${if (sortMode == SortMode.RECEIVED) "✓" else ""}") },
                                onClick = {
                                    sortMode = SortMode.RECEIVED
                                    showSortMenu = false
                                }
                            )
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
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Scroll to Top") } },
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
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to Top")
                        }
                    }

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Scroll to Bottom") } },
                        state = rememberTooltipState()
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(filteredNotifs.size - 1)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom")
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Search")
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank() && selectedReasonFilter == null && selectedPackages == null)
                                "No dismissed notifications logged yet"
                            else
                                "No matching dismissed logs found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isBlank() && selectedReasonFilter == null && selectedPackages == null) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Try searching for things you usually lose:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                listOf("OTP", "Address", "Confirmation", "Flight").forEach { example ->
                                    Text(
                                        text = example,
                                        modifier = Modifier
                                            .clickable { searchQuery = example }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "💡 Tip: NotiMind acts as 'Notification Insurance' — it automatically backs up notifications as you dismiss them, so you never lose critical info.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredNotifs,
                        key = { item -> "history_${item.key}_${item.id}" }
                    ) { item ->
                        val cardExpanded = expandedCards.contains(item.key)
                        LogHistoryCard(
                            item = item,
                            dateTimeFormatter = dateTimeFormatter,
                            dao = dao,
                            isExpanded = cardExpanded,
                            onToggleExpand = {
                                expandedCards = if (cardExpanded) expandedCards - item.key else expandedCards + item.key
                            }
                        )
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
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
            .clickable {
                NotificationLauncher.launchNotification(context, item.packageName, item.key, item.intentUri)
            },
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
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(if (item.isPinned) "Unpin notification" else "Pin notification") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    dao.updatePinnedStatus(item.key, !item.isPinned)
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (item.isPinned) "Unpin" else "Pin",
                                tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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

                    Spacer(modifier = Modifier.width(6.dp))
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(if (isExpanded) "Collapse details" else "Expand details") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
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
                        maxLines = 2,
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateTimeFormatter.format(Instant.ofEpochMilli(item.postTime)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = getPriorityLabel(item.priority),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
