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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import com.jeffers.notimindlite.ui.components.NotificationGroupCard
import com.jeffers.notimindlite.ui.components.groupNotifications
import org.json.JSONArray
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction

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
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.ui.dialogs.appPackageSelectorDialog
import com.jeffers.notimindlite.ui.components.ActiveFilterChip
import com.jeffers.notimindlite.ui.components.activeFilterChipsRow
import com.jeffers.notimindlite.ui.components.ActiveFirstRunEmptyState
import com.jeffers.notimindlite.ui.components.ActivePermissionEmptyState
import com.jeffers.notimindlite.ui.components.ActiveSearchEmptyState
import com.jeffers.notimindlite.ui.components.actionableChips

import com.jeffers.notimindlite.domain.search.HybridSearchEngine
import com.jeffers.notimindlite.util.NotificationLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private const val REASON_CODE_OFFSET = 1
private const val PRIORITY_MIN = -2
private const val PRIORITY_LOW = -1
private const val PRIORITY_DEFAULT = 0
private const val PRIORITY_HIGH = 1
private const val PRIORITY_MAX = 2
private const val SEARCH_MIN_LENGTH = 2
private const val MAX_SEARCH_SUGGESTIONS = 4
private const val SEARCH_DEBOUNCE_MS = 100L

private val REASON_LABELS = listOf(
    R.string.reason_1,
    R.string.reason_2,
    R.string.reason_3,
    R.string.reason_4,
    R.string.reason_5,
    R.string.reason_6,
    R.string.reason_7,
    R.string.reason_8,
    R.string.reason_9,
    R.string.reason_10,
    R.string.reason_11,
    R.string.reason_12,
    R.string.reason_13,
    R.string.reason_14,
    R.string.reason_15,
    R.string.reason_16,
    R.string.reason_17,
    R.string.reason_18,
    R.string.reason_19,
    R.string.reason_20,
    R.string.reason_21,
    R.string.reason_22,
    R.string.reason_23,
    R.string.reason_24
).mapIndexed { index, label -> index + REASON_CODE_OFFSET to label }.toMap()

fun getReasonLabel(reason: Int?): Int {
    return reason?.let { REASON_LABELS[it] } ?: if (reason == null) {
        R.string.reason_system
    } else {
        R.string.reason_unknown
    }
}

fun getPriorityLabel(priority: Int): String {
    return when (priority) {
        PRIORITY_MIN -> "Min"
        PRIORITY_LOW -> "Low"
        PRIORITY_DEFAULT -> "Default"
        PRIORITY_HIGH -> "High"
        PRIORITY_MAX -> "Max"
        else -> "Unknown ($priority)"
    }
}

private fun notificationSectionOrder(
    expandedSection: String,
    hasPinnedNotifications: Boolean
): List<NotificationSection> {
    val defaultList = if (hasPinnedNotifications) {
        listOf(
            NotificationSection.PINNED,
            NotificationSection.ACTIVE,
            NotificationSection.FILTERED,
            NotificationSection.DISMISSED,
            NotificationSection.LOST
        )
    } else {
        listOf(
            NotificationSection.ACTIVE,
            NotificationSection.FILTERED,
            NotificationSection.DISMISSED,
            NotificationSection.LOST
        )
    }
    val selected = defaultList.find { it.keyName == expandedSection }
    return if (selected != null) listOf(selected) + defaultList.filter { it != selected } else defaultList
}

private fun notificationSearchSuggestions(
    query: String,
    notifications: List<NotificationEntity>
): List<String> {
    if (query.length < SEARCH_MIN_LENGTH) return emptyList()
    return (notifications.map { it.appName } + notifications.map { it.title })
        .filter { it.contains(query, ignoreCase = true) }
        .distinct()
        .take(MAX_SEARCH_SUGGESTIONS)
}

enum class NotificationSection(val keyName: String, val title: String, val subtitle: String) {
    PINNED("PINNED", "Pinned Notifications", "Flagged & saved notifications for later reference"),
    ACTIVE("ACTIVE", "Active Notifications", "Currently active status bar notifications (sorted by time received)"),
    FILTERED("FILTERED", "Filtered Notifications", "System, clutter, spam, and auto-filtered notifications"),
    DISMISSED(
        "DISMISSED",
        "Recently Dismissed",
        "User swiped, clicked, or cleared notifications (sorted by time dismissed)"
    ),
    LOST("LOST", "Lost Notifications", "App cancelled or package changed notifications (sorted by time dismissed)")
}

@Composable
fun appIconImage(appIconUri: String?, modifier: Modifier = Modifier.size(20.dp)) {
    val imageBitmap = remember(appIconUri) {
        if (!appIconUri.isNullOrEmpty()) {
            try {
                BitmapFactory.decodeFile(appIconUri)?.asImageBitmap()
            } catch (e: SecurityException) {
                Log.w("ActiveNotifications", "Unable to read notification icon", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w("ActiveNotifications", "Invalid notification icon path", e)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun activeNotificationsScreen(dao: NotificationDao, preferences: PreferencesRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefManager = remember { PreferenceManager(context) }
    val compactMode by preferences.displayManager.compactMode.collectAsState()
    val showAppIcons by preferences.displayManager.showAppIcons.collectAsState()
    val groupByApp by preferences.displayManager.groupByApp.collectAsState()
    val sortOrder by preferences.displayManager.sortOrder.collectAsState()
    val previewLength by preferences.displayManager.previewLength.collectAsState()

    // F-K fix: persist user-meaningful state across process death / rotation.
    // - expandedSection: user's last-toggled section (was lost; PrefManager was only
    //   read on initial composition, never written back on toggle).
    // - searchQuery, isSearchExplicitlyOpened, selectedPackages: user input state.
    // - isGranted, expandedCards, dismissingKeys, showBackupKeyDialog, currentBackupKey,
    //   currentPendingSecretKey, showPackagePicker, debouncedSearchQuery, isSearchFocused:
    //   transient UI / derived / security-sensitive; do not save.
    var expandedSection by rememberSaveable { mutableStateOf(prefManager.getExpandedSection()) }
    var expandedCards by remember { mutableStateOf(setOf<String>()) }
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }
    var isGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var searchQuery by rememberSaveable { mutableStateOf(preferences.sharedSearchManager.query.value) }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var isSearchExplicitlyOpened by rememberSaveable { mutableStateOf(false) }
    val recentSearches by preferences.sharedSearchManager.recentSearches.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchQuery) { preferences.sharedSearchManager.setQuery(searchQuery) }



    LaunchedEffect(searchQuery) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedSearchQuery = searchQuery
    }


    // F-K fix: selectedPackages is user filter state — persist across process death.
    var selectedPackages by remember { mutableStateOf(preferences.sharedSearchManager.packages.value) }
    var showPackagePicker by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPackages) { preferences.sharedSearchManager.setPackages(selectedPackages) }

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
    val filteredNotifs by dao.getFilteredNotificationsFlow().collectAsState(initial = emptyList())
    val recentlyDismissed by dao.getRecentlyDismissedFlow().collectAsState(initial = emptyList())
    val lostNotifs by dao.getLostNotificationsFlow().collectAsState(initial = emptyList())
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss", Locale.getDefault()).withZone(ZoneId.systemDefault())
    }

    val allActiveList = remember(pinnedNotifs, activeNotifs, filteredNotifs, recentlyDismissed, lostNotifs) {
        (pinnedNotifs + activeNotifs + filteredNotifs + recentlyDismissed + lostNotifs).distinctBy { it.packageName }
    }

    val availableApps = remember(allActiveList) {
        allActiveList.map { it.packageName to it.appName }
    }

    val scope = rememberCoroutineScope()

    val sectionOrder = remember(expandedSection, pinnedNotifs.size) {
        notificationSectionOrder(expandedSection, pinnedNotifs.isNotEmpty())
    }

    val listState = rememberLazyListState()

    fun toggleSection(sectionKey: String) {
        val newExpanded = if (expandedSection == sectionKey) "NONE" else sectionKey
        expandedSection = newExpanded
        prefManager.setExpandedSection(newExpanded)
    }

    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    // Auto-hide when keyboard is dismissed (IME no longer visible) AND the field lost focus
    LaunchedEffect(isSearchFocused, imeVisible, searchQuery) {
        if (!imeVisible && !isSearchFocused && searchQuery.isBlank()) {
            isSearchExplicitlyOpened = false
        }
    }

    Scaffold(
        topBar = {
            activeNotificationsTopBar(
                selectedPackages = selectedPackages,
                searchQuery = searchQuery,
                onClearFilters = {
                    selectedPackages = null
                    searchQuery = ""
                    isSearchFocused = false
                    isSearchExplicitlyOpened = false
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
                onOpenPackagePicker = { showPackagePicker = true },
                onToggleSearch = {
                    if (isSearchExplicitlyOpened || searchQuery.isNotEmpty() || isSearchFocused) {
                        isSearchExplicitlyOpened = false
                        isSearchFocused = false
                        searchQuery = ""
                    } else {
                        isSearchExplicitlyOpened = true
                        scope.launch { listState.animateScrollToItem(0); searchFocusRequester.requestFocus() }
                    }
                }
            )
        },
        floatingActionButton = { activeNotificationsFab(listState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val searchSuggestions = remember(
                searchQuery,
                pinnedNotifs,
                activeNotifs,
                filteredNotifs,
                recentlyDismissed,
                lostNotifs
            ) {
                notificationSearchSuggestions(
                    searchQuery,
                    pinnedNotifs + activeNotifs + filteredNotifs + recentlyDismissed + lostNotifs
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                activeSearchHeader(
                    ActiveSearchHeaderConfig(
                        searchQuery = searchQuery,
                        searchSuggestions = searchSuggestions,
                        recentSearches = recentSearches,
                        isVisible = isSearchExplicitlyOpened || searchQuery.isNotEmpty() || isSearchFocused,
                        focusRequester = searchFocusRequester,
                        preferences = preferences,
                        onQueryChanged = { searchQuery = it },
                        onFocusChanged = { isSearchFocused = it },
                        onDone = {
                            val query = searchQuery.trim()
                            if (query.isNotEmpty()) preferences.sharedSearchManager.addRecentSearch(query)
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    )
                )

                // F-N usability [2026-09-06]: surface empty-state Composables when the
                // entire DB is empty or a query returned no matches. Distinguishes three
                // scenarios: (1) permission missing → guidance + CTA, (2) permission OK
                // but no notifications captured yet → first-run guidance, (3) permission
                // OK + DB has rows but a query/filter returned nothing → search-empty
                // guidance with a clear-search escape hatch.
                val hasAnyData = activeNotifs.isNotEmpty() ||
                    filteredNotifs.isNotEmpty() ||
                    pinnedNotifs.isNotEmpty() ||
                    recentlyDismissed.isNotEmpty() ||
                    lostNotifs.isNotEmpty()
                val isSearching = debouncedSearchQuery.isNotBlank() ||
                    !selectedPackages.isNullOrEmpty()
                if (!hasAnyData) {
                    item(key = "active_empty_state") {
                        activeEmptyState(
                            isGranted = isGranted,
                            isSearching = isSearching,
                            debouncedSearchQuery = debouncedSearchQuery,
                            onGrantClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            onClearClick = {
                                searchQuery = ""
                                selectedPackages = null
                            }
                        )
                    }
                }

                activeFilterChipsItem(
                    searchQuery = debouncedSearchQuery,
                    selectedPackages = selectedPackages,
                    onClearSearch = { searchQuery = "" },
                    onClearPackages = { selectedPackages = null },
                    onClearAll = {
                        searchQuery = ""
                        selectedPackages = null
                    }
                )

                renderNotificationSections(
                    ActiveNotificationSectionsConfig(
                        sectionOrder = sectionOrder,
                        expandedSection = expandedSection,
                        pinnedNotifs = pinnedNotifs,
                        activeNotifs = activeNotifs,
                        filteredNotifs = filteredNotifs,
                        recentlyDismissed = recentlyDismissed,
                        lostNotifs = lostNotifs,
                        selectedPackages = selectedPackages,
                        debouncedSearchQuery = debouncedSearchQuery,
                        groupByApp = groupByApp,
                        sortOrder = sortOrder,
                        collapsedGroups = collapsedGroups,
                        setCollapsedGroups = { collapsedGroups = it },
                        expandedCards = expandedCards,
                        setExpandedCards = { expandedCards = it },
                        dateTimeFormatter = dateTimeFormatter,
                        dao = dao,
                        showAppIcons = showAppIcons,
                        compactMode = compactMode,
                        previewLength = previewLength,
                        toggleSection = ::toggleSection
                    )
                )
            }
        }
    }

    if (showPackagePicker) {
        appPackageSelectorDialog(
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

data class LogNotificationCardConfig(
    val item: NotificationEntity,
    val dateTimeFormatter: DateTimeFormatter,
    val dao: NotificationDao,
    val isExpanded: Boolean,
    val highlightQuery: String = "",
    val onToggleExpand: () -> Unit,
    val showAppIcon: Boolean = true,
    val compactMode: Boolean = false,
    val previewLength: Int = 140
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun logNotificationCard(config: LogNotificationCardConfig) {
    val item = config.item
    val dateTimeFormatter = config.dateTimeFormatter
    val dao = config.dao
    val isExpanded = config.isExpanded
    val highlightQuery = config.highlightQuery
    val onToggleExpand = config.onToggleExpand
    val showAppIcon = config.showAppIcon
    val compactMode = config.compactMode
    val previewLength = config.previewLength
    
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
        Column(modifier = Modifier.padding(if (compactMode) 8.dp else 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeNotificationHeader(config)
            }

            if (!isExpanded) {
                notificationCollapsedContent(config)
            }

            if (isExpanded) {
                activeNotificationExpandedContent(
                    item = item,
                    dateTimeFormatter = dateTimeFormatter,
                    highlightQuery = highlightQuery
                )
            }
        }
    }
}

@Composable
private fun notificationCollapsedContent(config: LogNotificationCardConfig) {
    val item = config.item
    val highlightQuery = config.highlightQuery
    val previewLength = config.previewLength

    if (item.title.isNotEmpty()) {
        Text(
            text = highlightSearchText(item.title, highlightQuery),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    if (item.content.isNotEmpty()) {
        Text(
            text = highlightSearchText(item.content.take(previewLength), highlightQuery),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
