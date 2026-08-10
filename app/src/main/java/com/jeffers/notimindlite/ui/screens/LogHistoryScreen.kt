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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
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
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.NotificationLauncher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
    var sortMode by remember { mutableStateOf(SortMode.DISMISSED) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }

    val dismissedNotifsDismissed by dao.getDismissedNotificationsSortedByDismissed().collectAsState(initial = emptyList())
    val dismissedNotifsReceived by dao.getDismissedNotificationsSortedByReceived().collectAsState(initial = emptyList())
    val totalCount by dao.getDismissedNotificationCountFlow().collectAsState(initial = 0)

    val activeList = if (sortMode == SortMode.DISMISSED) dismissedNotifsDismissed else dismissedNotifsReceived
    var searchQuery by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    val filteredNotifs = remember(activeList, searchQuery) {
        if (searchQuery.isBlank()) {
            activeList
        } else {
            activeList.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.content.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log History ($totalCount)", fontWeight = FontWeight.Bold) },
                actions = {
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
                                    scope.launch {
                                        val allLogs = dao.getAllNotifications().first()
                                        DatabaseExporter.shareExportFile(context, allLogs, isJson = true)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Logs to CSV") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch {
                                        val allLogs = dao.getAllNotifications().first()
                                        DatabaseExporter.shareExportFile(context, allLogs, isJson = false)
                                    }
                                }
                            )
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
                placeholder = { Text("Search dismissed logs (${sortMode.label})...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
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
                            text = if (searchQuery.isBlank()) "No dismissed notifications logged yet" else "No matching dismissed logs found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNotifs, key = { it.id }) { item ->
                        val cardExpanded = expandedCards.contains(item.key)
                        LogHistoryCard(
                            item = item,
                            dateFormat = dateFormat,
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
}

@Composable
fun LogHistoryCard(
    item: NotificationEntity,
    dateFormat: SimpleDateFormat,
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
                Text(
                    text = item.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

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

            // Enhanced detail panel with spring micro-animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + androidx.compose.animation.expandVertically(animationSpec = spring(stiffness = 300f)),
                exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = 300f))
            ) {
                NotificationDetailPanel(item = item, dateFormat = dateFormat)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Received: ${dateFormat.format(Date(item.postTime))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                val dismissTimestamp = item.dismissTime ?: item.postTime
                Text(
                    text = "Dismissed: ${dateFormat.format(Date(dismissTimestamp))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
