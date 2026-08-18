package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.ui.dialogs.AppPackageSelectorDialog
import com.jeffers.notimindlite.ui.components.ActionableChips
import com.jeffers.notimindlite.ui.components.SpeedDialSettingsFab
import com.jeffers.notimindlite.util.HybridSearchEngine
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

fun checkNotificationPermission(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat != null && flat.contains(context.packageName)
}

fun getReasonLabel(reason: Int?): String {
    return when (reason) {
        1 -> "User Swiped"
        2 -> "Cleared All"
        3 -> "User Clicked"
        4 -> "Listener Cancelled"
        5 -> "Package Changed"
        6 -> "Package Banned"
        7 -> "User Banned"
        8 -> "App Cancelled"
        9 -> "App Cancelled All"
        10 -> "Timeout"
        11 -> "Channel Banned"
        12 -> "Snoozed"
        13 -> "Group Summary Canceled"
        14 -> "Listener Muted"
        15 -> "Clearable Group Summary"
        16 -> "Channel Changed"
        17 -> "Group Threshold"
        18 -> "Assistant Cancelled"
        19 -> "User Cancelled"
        20 -> "Profile Turned Off"
        21 -> "Package Uninstalled"
        22 -> "App Disallowed"
        23 -> "User Dismissed"
        24 -> "Review Dismissed"
        else -> if (reason != null) "Reason #$reason" else "System Dismissed"
    }
}

fun getPriorityLabel(priority: Int): String {
    return when (priority) {
        -2 -> "Min"
        -1 -> "Low"
        0 -> "Default"
        1 -> "High"
        2 -> "Max"
        else -> "Unknown ($priority)"
    }
}

enum class NotificationSection(val keyName: String, val title: String, val subtitle: String) {
    PINNED("PINNED", "Pinned Notifications", "Flagged & saved notifications for later reference"),
    ACTIVE("ACTIVE", "Active Notifications", "Currently active status bar notifications (sorted by time received)"),
    FILTERED("FILTERED", "Filtered Notifications", "System, clutter, spam, and auto-filtered notifications"),
    DISMISSED("DISMISSED", "Recently Dismissed", "User swiped, clicked, or cleared notifications (sorted by time dismissed)"),
    LOST("LOST", "Lost Notifications", "App cancelled or package changed notifications (sorted by time dismissed)")
}

@Composable
fun AppIconImage(appIconUri: String?, modifier: Modifier = Modifier.size(20.dp)) {
    val imageBitmap = remember(appIconUri) {
        if (!appIconUri.isNullOrEmpty()) {
            try {
                BitmapFactory.decodeFile(appIconUri)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "App Icon",
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActiveNotificationsScreen(dao: NotificationDao, authManager: AuthManager, db: AppDatabase) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefManager = remember { PreferenceManager(context) }

    var expandedSection by remember { mutableStateOf(prefManager.getExpandedSection()) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }
    var isGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var isSearchExplicitlyOpened by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        delay(100L)
        debouncedSearchQuery = searchQuery
    }

    var selectedPackages by remember { mutableStateOf<List<String>?>(null) }
    var showPackagePicker by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = checkNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pinnedNotifs by dao.getPinnedNotificationsFlow().collectAsState(initial = emptyList())
    val activeNotifs by dao.getActiveNotificationsFlow().collectAsState(initial = emptyList())
    val recentlyDismissed by dao.getRecentlyDismissedFlow().collectAsState(initial = emptyList())
    val lostNotifs by dao.getLostNotificationsFlow().collectAsState(initial = emptyList())
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss", Locale.getDefault()).withZone(ZoneId.systemDefault())
    }

    val allActiveList = remember(pinnedNotifs, activeNotifs, recentlyDismissed, lostNotifs) {
        (pinnedNotifs + activeNotifs + recentlyDismissed + lostNotifs).distinctBy { it.packageName }
    }

    val availableApps = remember(allActiveList) {
        allActiveList.map { it.packageName to it.appName }
    }

    var dismissingKeys by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    val sectionOrder = remember(expandedSection, pinnedNotifs.size) {
        val defaultList = if (pinnedNotifs.isNotEmpty()) {
            listOf(NotificationSection.PINNED, NotificationSection.ACTIVE, NotificationSection.FILTERED, NotificationSection.DISMISSED, NotificationSection.LOST)
        } else {
            listOf(NotificationSection.ACTIVE, NotificationSection.FILTERED, NotificationSection.DISMISSED, NotificationSection.LOST)
        }
        val selected = defaultList.find { it.keyName == expandedSection }
        if (selected != null) {
            listOf(selected) + defaultList.filter { it != selected }
        } else {
            defaultList
        }
    }

    val totalLazyItemCount = remember(sectionOrder, expandedSection, pinnedNotifs, activeNotifs, recentlyDismissed, lostNotifs, selectedPackages, debouncedSearchQuery) {
        var count = 2
        sectionOrder.forEach { section ->
            count += 1
            if (expandedSection == section.keyName) {
                val rawList = when (section) {
                    NotificationSection.PINNED -> pinnedNotifs
                    NotificationSection.ACTIVE -> activeNotifs
                    NotificationSection.FILTERED -> emptyList()
                    NotificationSection.DISMISSED -> recentlyDismissed
                    NotificationSection.LOST -> lostNotifs
                }.distinctBy { "${it.packageName}_${it.title}_${it.content}" }
                val filtered = if (!selectedPackages.isNullOrEmpty()) rawList.filter { selectedPackages!!.contains(it.packageName) } else rawList
                val items = if (debouncedSearchQuery.isBlank()) filtered else HybridSearchEngine.searchAndRank(filtered, debouncedSearchQuery)
                count += if (items.isEmpty()) 1 else items.size
            }
        }
        count
    }

    val listState = rememberLazyListState()

    fun toggleSection(sectionKey: String) {
        val newExpanded = if (expandedSection == sectionKey) "NONE" else sectionKey
        expandedSection = newExpanded
        prefManager.setExpandedSection(newExpanded)
    }

    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotiMind", fontWeight = FontWeight.Bold) },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Filter Apps") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { showPackagePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter Apps",
                                tint = if (!selectedPackages.isNullOrEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Search Notifications") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            isSearchExplicitlyOpened = !isSearchExplicitlyOpened
                            if (isSearchExplicitlyOpened) {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                    searchFocusRequester.requestFocus()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotEmpty() || isSearchExplicitlyOpened || isSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            SpeedDialSettingsFab(
                onSyncClick = { },
                onBackupClick = { },
                onSettingsClick = { }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item(key = "active_search_header") {
                AnimatedVisibility(
                    visible = isSearchExplicitlyOpened || searchQuery.isNotEmpty() || isSearchFocused,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val searchSuggestions = remember(searchQuery, pinnedNotifs, activeNotifs, recentlyDismissed, lostNotifs) {
                        if (searchQuery.length < 2) emptyList()
                        else {
                            val allNotifs = pinnedNotifs + activeNotifs + recentlyDismissed + lostNotifs
                            (allNotifs.map { it.appName } + allNotifs.map { it.title })
                                .filter { it.contains(searchQuery, ignoreCase = true) }
                                .distinct()
                                .take(4)
                        }
                    }
                    var expandedDropdown by remember { mutableStateOf(false) }

                    LaunchedEffect(searchSuggestions) {
                        expandedDropdown = searchSuggestions.isNotEmpty()
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .focusRequester(searchFocusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
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
                }
            }

            item(key = "service_status_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGranted)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Listener Service",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = if (isGranted) "Status: Active & Listening" else "Status: Permission Required",
                                fontSize = 12.sp,
                                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(if (isGranted) "Settings" else "Grant")
                        }
                    }
                }
            }

            sectionOrder.forEach { section ->
                val isExpanded = expandedSection == section.keyName
                val rawItemsList = when (section) {
                    NotificationSection.PINNED -> pinnedNotifs
                    NotificationSection.ACTIVE -> activeNotifs
                    NotificationSection.FILTERED -> emptyList()
                    NotificationSection.DISMISSED -> recentlyDismissed
                    NotificationSection.LOST -> lostNotifs
                }.distinctBy { "${it.packageName}_${it.title}_${it.content}" }

                val filteredList = if (!selectedPackages.isNullOrEmpty()) {
                    rawItemsList.filter { selectedPackages!!.contains(it.packageName) }
                } else {
                    rawItemsList
                }

                val itemsList = if (debouncedSearchQuery.isBlank()) filteredList
                else HybridSearchEngine.searchAndRank(filteredList, debouncedSearchQuery)

                stickyHeader(key = "sticky_header_${section.keyName}") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        shadowElevation = if (isExpanded) 2.dp else 0.dp
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { toggleSection(section.keyName) },
                            colors = CardDefaults.cardColors(
                                containerColor = when (section) {
                                    NotificationSection.PINNED -> MaterialTheme.colorScheme.primaryContainer
                                    NotificationSection.ACTIVE -> MaterialTheme.colorScheme.secondaryContainer
                                    NotificationSection.FILTERED -> MaterialTheme.colorScheme.surfaceVariant
                                    NotificationSection.DISMISSED -> MaterialTheme.colorScheme.tertiaryContainer
                                    NotificationSection.LOST -> MaterialTheme.colorScheme.errorContainer
                                }
                            )
                        ) {
                            val contentColor = when (section) {
                                NotificationSection.PINNED -> MaterialTheme.colorScheme.onPrimaryContainer
                                NotificationSection.ACTIVE -> MaterialTheme.colorScheme.onSecondaryContainer
                                NotificationSection.FILTERED -> MaterialTheme.colorScheme.onSurfaceVariant
                                NotificationSection.DISMISSED -> MaterialTheme.colorScheme.onTertiaryContainer
                                NotificationSection.LOST -> MaterialTheme.colorScheme.onErrorContainer
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${section.title} (${itemsList.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
