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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.util.SemanticSearchHelper
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
fun ActiveNotificationsScreen(dao: NotificationDao) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefManager = remember { PreferenceManager(context) }

    var expandedSection by remember { mutableStateOf(prefManager.getExpandedSection()) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }
    var isGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExplicitlyOpened by remember { mutableStateOf(false) }

    // Auto refresh permission state on ON_RESUME
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

    // Track dismissing items for swipe animation
    var dismissingKeys by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // Dynamic ordering: placing the expanded section at the top, auto-hide empty Pinned
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

    val listState = rememberLazyListState()

    fun toggleSection(sectionKey: String) {
        val newExpanded = if (expandedSection == sectionKey) "NONE" else sectionKey
        expandedSection = newExpanded
        prefManager.setExpandedSection(newExpanded)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active & Categorized Notifications", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        isSearchExplicitlyOpened = !isSearchExplicitlyOpened
                        if (isSearchExplicitlyOpened) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotEmpty() || isSearchExplicitlyOpened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
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
                            listState.animateScrollToItem(100)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom")
                }
            }
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
            // Hidden-by-default Semantic Search Bar (Revealed when scrolling to the top above cards or clicking search icon)
            item(key = "active_search_header") {
                AnimatedVisibility(
                    visible = isSearchExplicitlyOpened || searchQuery.isNotEmpty() || listState.firstVisibleItemIndex == 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        placeholder = { Text("Semantic search (e.g. 'uber', 'payment', 'code')...") },
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
                }
            }

            // Service Status Card
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
                                containerColor = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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

                // Apply simple semantic search matching
                val itemsList = remember(rawItemsList, searchQuery) {
                    if (searchQuery.isBlank()) rawItemsList
                    else rawItemsList.filter { SemanticSearchHelper.matches(it, searchQuery) }
                }

                // Persistent Sticky Section Header (Keeps open section header accessible at all times for easy collapse)
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
                                    NotificationSection.PINNED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                                    NotificationSection.ACTIVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                    NotificationSection.FILTERED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                    NotificationSection.DISMISSED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                    NotificationSection.LOST -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                                }
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
                                        text = "${section.title} (${itemsList.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = section.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Expanded Section Content Items
                if (isExpanded) {
                    if (itemsList.isEmpty()) {
                        item(key = "empty_${section.keyName}") {
                            Text(
                                text = if (searchQuery.isBlank()) "No ${section.title.lowercase()} logged" else "No matching notifications in ${section.title.lowercase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                    } else {
                        items(
                            items = itemsList,
                            key = { item -> "${section.keyName}_${item.key}_${item.id}" }
                        ) { item ->
                            val cardExpanded = expandedCards.contains(item.key)
                            val isDismissing = dismissingKeys.contains(item.key)

                            AnimatedVisibility(
                                visible = !isDismissing,
                                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = 300f)),
                                exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(200))
                            ) {
                                SystemSwipeToDismissCard(
                                    item = item,
                                    dateTimeFormatter = dateTimeFormatter,
                                    section = section,
                                    dao = dao,
                                    isExpanded = cardExpanded,
                                    onToggleExpand = {
                                        expandedCards = if (cardExpanded) expandedCards - item.key else expandedCards + item.key
                                    },
                                    onDismiss = {
                                        dismissingKeys = dismissingKeys + item.key
                                        scope.launch {
                                            NotificationLoggerService.dismissNotification(item.key)
                                            delay(300)
                                            dao.markDismissedWithReasonByMatching(
                                                key = item.key,
                                                packageName = item.packageName,
                                                title = item.title,
                                                content = item.content,
                                                reason = 1 // User Swiped
                                            )
                                            dismissingKeys = dismissingKeys - item.key
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Container supporting interactive swipe-to-dismiss gesture for active clearable notifications
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSwipeToDismissCard(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter,
    section: NotificationSection,
    dao: NotificationDao,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    if (section == NotificationSection.ACTIVE && !item.isDismissed && item.isClearable) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissValue ->
                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color by animateColorAsState(
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    label = "swipe_bg_color"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Swipe to Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            },
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true
        ) {
            UnifiedNotificationCard(
                item = item,
                dateTimeFormatter = dateTimeFormatter,
                section = section,
                dao = dao,
                isExpanded = isExpanded,
                onToggleExpand = onToggleExpand,
                onDismiss = onDismiss
            )
        }
    } else {
        UnifiedNotificationCard(
            item = item,
            dateTimeFormatter = dateTimeFormatter,
            section = section,
            dao = dao,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun UnifiedNotificationCard(
    item: NotificationEntity,
    dateTimeFormatter: DateTimeFormatter,
    section: NotificationSection,
    dao: NotificationDao,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Micro-animation scale for pin bookmark toggle
    val pinScale by animateFloatAsState(if (item.isPinned) 1.2f else 1.0f, animationSpec = spring(stiffness = 400f), label = "pin_scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                NotificationLauncher.launchNotification(context, item.packageName, item.key, item.intentUri)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        fontSize = 14.sp,
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
                        modifier = Modifier
                            .size(24.dp)
                            .scale(pinScale)
                    ) {
                        Icon(
                            imageVector = if (item.isPinned) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (item.isPinned) "Unpin" else "Pin",
                            tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Only show dismiss X if notification is active AND clearable
                    if (!item.isDismissed && item.isClearable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Notification",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (section != NotificationSection.ACTIVE && item.isDismissed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val badgeColor = if (section == NotificationSection.LOST)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary

                        Surface(
                            color = badgeColor.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = getReasonLabel(item.dismissReason),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (item.isPersistent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Ongoing",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFE65100)
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

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!item.subText.isNullOrEmpty()) {
                Text(
                    text = item.subText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.bigText ?: item.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Enhanced full metadata details section with spring expand micro-animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = 300f)),
                exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = 300f))
            ) {
                NotificationDetailPanel(item = item, dateTimeFormatter = dateTimeFormatter)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Received: ${dateTimeFormatter.format(Instant.ofEpochMilli(item.postTime))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (item.isDismissed) {
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
}

/**
 * Enhanced notification detail panel organized into logical groups
 * with human-readable labels and visual hierarchy.
 */
@Composable
fun NotificationDetailPanel(item: NotificationEntity, dateTimeFormatter: DateTimeFormatter) {
    val context = LocalContext.current

    // Parse action labels from JSON
    val actionLabels = remember(item.actionLabels) {
        if (!item.actionLabels.isNullOrEmpty()) {
            try {
                val jsonArray = org.json.JSONArray(item.actionLabels)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))

        // ── Actions ──
        if (actionLabels.isNotEmpty()) {
            DetailSectionHeader("Actions")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionLabels.forEachIndexed { index, label ->
                    OutlinedButton(
                        onClick = {
                            val triggered = NotificationLauncher.triggerAction(context, item.key, index)
                            if (!triggered) {
                                android.widget.Toast.makeText(context, "Action expired — open the app instead", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Source Information ──
        DetailSectionHeader("Source")
        DetailChip("App", item.appName)
        DetailChip("Package", item.packageName)
        DetailChip("Channel", item.channelId ?: "Default")
        if (!item.category.isNullOrEmpty()) {
            DetailChip("Category", item.category)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Behavior & Priority ──
        DetailSectionHeader("Behavior")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                label = getPriorityLabel(item.priority),
                color = when {
                    item.priority >= 1 -> MaterialTheme.colorScheme.error
                    item.priority == 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (item.isOngoing) StatusPill("Ongoing", Color(0xFFE65100))
            if (!item.isClearable) StatusPill("Non-Clearable", MaterialTheme.colorScheme.error)
            if (item.isPinned) StatusPill("Pinned", MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Grouping ──
        if (!item.groupKey.isNullOrEmpty()) {
            DetailSectionHeader("Grouping")
            DetailChip("Group", item.groupKey)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Internal Reference ──
        DetailSectionHeader("Internal")
        DetailChip("Notification Key", item.key)
        if (!item.intentUri.isNullOrEmpty()) {
            DetailChip("Launch Intent", item.intentUri, maxLines = 2)
        }
    }
}

@Composable
fun DetailSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DetailChip(label: String, value: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
