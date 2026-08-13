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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.dialogs.AppPackageSelectorDialog
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.HybridSearchEngine
import com.jeffers.notimindlite.util.NotificationLauncher
import kotlinx.coroutines.Dispatchers
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
fun LogHistoryScreen(dao: NotificationDao) {
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

    val dismissedNotifsDismissed by dao.getDismissedNotificationsSortedByDismissed().collectAsState(initial = emptyList())
    val dismissedNotifsReceived by dao.getDismissedNotificationsSortedByReceived().collectAsState(initial = emptyList())
    val totalCount by dao.getDismissedNotificationCountFlow().collectAsState(initial = 0)

    val activeList = if (sortMode == SortMode.DISMISSED) dismissedNotifsDismissed else dismissedNotifsReceived
    var searchQuery by remember { mutableStateOf("") }
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss", Locale.getDefault()).withZone(ZoneId.systemDefault())
    }

    val availableReasons = remember(activeList) {
        activeList.mapNotNull { it.dismissReason }.distinct().sorted()
    }

    val availableApps = remember(activeList) {
        activeList.map { it.packageName to it.appName }.distinctBy { it.first }
    }

    val filteredNotifs = remember(activeList, searchQuery, selectedReasonFilter, selectedPackages) {
        var list = activeList.distinctBy { "${it.packageName}_${it.title}_${it.content}" }

        if (selectedReasonFilter != null) {
            list = list.filter { it.dismissReason == selectedReasonFilter }
        }

        if (!selectedPackages.isNullOrEmpty()) {
            list = list.filter { selectedPackages!!.contains(it.packageName) }
        }

        if (searchQuery.isBlank()) {
            list
        } else {
            HybridSearchEngine.searchAndRank(list, searchQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log History (${filteredNotifs.size}/$totalCount)", fontWeight = FontWeight.Bold) },
                actions = {
                    // App Package Filter Button
                    IconButton(onClick = { showPackagePicker = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter Apps",
                            tint = if (!selectedPackages.isNullOrEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Export Database Button
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.Download, contentDescription = "Export Database Logs")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Logs to JSON") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        val allLogs = dao.getAllNotificationsList()
                                        DatabaseExporter.shareExportFile(context, allLogs, isJson = true)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Logs to CSV") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        val allLogs = dao.getAllNotificationsList()
                                        DatabaseExporter.shareExportFile(context, allLogs, isJson = false)
                                    }
                                }
                            )
                        }
                    }

                    // Reason Filter Menu
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter by Dismiss Reason",
                                tint = if (selectedReasonFilter != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Reasons ${if (selectedReasonFilter == null) "✓" else ""}") },
                                onClick = {
                                    selectedReasonFilter = null
                                    showFilterMenu = false
                                }
                            )
                            availableReasons.forEach { reasonCode ->
                                DropdownMenuItem(
                                    text = { Text("${getReasonLabel(reasonCode)} (#$reasonCode) ${if (selectedReasonFilter == reasonCode) "✓" else ""}") },
                                    onClick = {
                                        selectedReasonFilter = reasonCode
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Sort Order Button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Log History")
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    val filterSuffix = selectedReasonFilter?.let { " • Filter: ${getReasonLabel(it)}" } ?: ""
                    val sortLabel = if (searchQuery.isNotBlank()) "Best Match" else sortMode.label
                    Text("Search logs ($sortLabel$filterSuffix)...")
                },
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

                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = getReasonLabel(item.dismissReason),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            if (!item.subText.isNullOrEmpty()) {
                Text(
                    text = item.subText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = item.bigText ?: item.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + androidx.compose.animation.expandVertically(animationSpec = spring(stiffness = 300f)),
                exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = 300f))
            ) {
                NotificationDetailPanel(item = item, dateTimeFormatter = dateTimeFormatter)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Received: ${dateTimeFormatter.format(Instant.ofEpochMilli(item.postTime))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                val dismissTimestamp = item.dismissTime ?: item.postTime
                Text(
                    text = "Dismissed: ${dateTimeFormatter.format(Instant.ofEpochMilli(dismissTimestamp))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
