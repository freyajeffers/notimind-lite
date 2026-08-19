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
import android.util.Log
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.ui.dialogs.AppPackageSelectorDialog
import com.jeffers.notimindlite.ui.components.ActionableChips
import com.jeffers.notimindlite.ui.components.SpeedDialSettingsFab
import com.jeffers.notimindlite.ui.components.BackupKeyDialog
import com.jeffers.notimindlite.data.local.generateBackupKey
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.SecretKey

fun checkNotificationPermission(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat != null && flat.contains(context.packageName)
}

fun getReasonLabel(reason: Int?): Int {
    return when (reason) {
        1 -> R.string.reason_1
        2 -> R.string.reason_2
        3 -> R.string.reason_3
        4 -> R.string.reason_4
        5 -> R.string.reason_5
        6 -> R.string.reason_6
        7 -> R.string.reason_7
        8 -> R.string.reason_8
        9 -> R.string.reason_9
        10 -> R.string.reason_10
        11 -> R.string.reason_11
        12 -> R.string.reason_12
        13 -> R.string.reason_13
        14 -> R.string.reason_14
        15 -> R.string.reason_15
        16 -> R.string.reason_16
        17 -> R.string.reason_17
        18 -> R.string.reason_18
        19 -> R.string.reason_19
        20 -> R.string.reason_20
        21 -> R.string.reason_21
        22 -> R.string.reason_22
        23 -> R.string.reason_23
        24 -> R.string.reason_24
        else -> if (reason != null) R.string.reason_unknown else R.string.reason_system
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

    var showBackupKeyDialog by remember { mutableStateOf(false) }
    var currentBackupKey by remember { mutableStateOf("") }
    var currentPendingSecretKey by remember { mutableStateOf<SecretKey?>(null) }

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

    val notificationsBySection by remember {
        derivedStateOf {
            NotificationSection.entries.associateWith { section ->
                val rawList = when (section) {
                    NotificationSection.PINNED -> pinnedNotifs
                    NotificationSection.ACTIVE -> activeNotifs
                    NotificationSection.FILTERED -> emptyList()
                    NotificationSection.DISMISSED -> recentlyDismissed
                    NotificationSection.LOST -> lostNotifs
                }.distinctBy { "${it.packageName}_${it.title}_${it.content}" }

                val filtered = if (!selectedPackages.isNullOrEmpty()) {
                    rawList.filter { selectedPackages!!.contains(it.packageName) }
                } else {
                    rawList
                }

                if (debouncedSearchQuery.isBlank()) {
                    filtered
                } else {
                    filtered.filter { it.title.contains(debouncedSearchQuery, ignoreCase = true) || it.content.contains(debouncedSearchQuery, ignoreCase = true) }
                }
            }
        }
    }

    val totalLazyItemCount by remember {
        derivedStateOf {
            var count = 2
            sectionOrder.forEach { section ->
                count += 1
                val items = notificationsBySection[section] ?: emptyList()
                count += if (items.isEmpty()) 1 else items.size
            }
            count
        }
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
                title = { Text(stringResource(id = R.string.active_notifications_title), fontWeight = FontWeight.Bold) },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(stringResource(id = R.string.active_notifications_filter_apps)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { showPackagePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(id = R.string.active_notifications_filter_apps),
                                tint = if (!selectedPackages.isNullOrEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(stringResource(id = R.string.active_notifications_search_placeholder)) } },
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
                                contentDescription = stringResource(id = R.string.common_search),
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
                onBackupClick = {
                    scope.launch {
                        try {
                            val secretKey = generateBackupKey()
                            val keyBase64 = com.jeffers.notimindlite.data.local.BackupKeyCodec.encode(secretKey)
                            
                            showBackupKeyDialog = true
                            currentBackupKey = keyBase64
                            currentPendingSecretKey = secretKey
                        } catch (e: Exception) {
                            Log.e("ActiveNotifications", "Backup key generation failed", e)
                        }
                    }
                },
                onSettingsClick = { }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    else {
                        filteredList.filter { it.title.contains(debouncedSearchQuery, ignoreCase = true) || it.content.contains(debouncedSearchQuery, ignoreCase = true) }
                    }

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
                                    .clickable { toggleSection(section.keyName) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = section.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }

                    if (isExpanded) {
                        items(
                            items = itemsList,
                            key = { item -> "item_${item.key}" }
                        ) { item ->
                            val cardExpanded = expandedCards.contains(item.key)
                            LogNotificationCard(
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
fun LogNotificationCard(
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
                        text = stringResource(id = R.string.reason_unknown),
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
