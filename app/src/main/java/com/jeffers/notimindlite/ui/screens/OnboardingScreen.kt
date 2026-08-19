package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import com.jeffers.notimindlite.data.local.PreferenceManager

@Composable
fun OnboardingScreen(
    preferenceManager: PreferenceManager,
    onCompleted: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Checklist state
    var notificationAccessGranted by remember { mutableStateOf(false) }
    var directBootConfigured by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ConceptPage(
                        title = "Notification Insurance",
                        description = "Ever dismissed a notification only to realize you needed it? NotiMind acts as insurance for your alerts, automatically backing up everything you dismiss so it's always searchable.",
                        icon = Icons.Default.NotificationsActive,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    1 -> ConceptPage(
                        title = "Hybrid Search",
                        description = "Stop scrolling through endless logs. Our Hybrid Search combines keyword matching with semantic ranking to find exactly what you're looking for, even if you don't remember the exact words.",
                        icon = Icons.Default.Search,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    )
                    2 -> PermissionPage(
                        title = "Step 1: Notification Access",
                        description = "To insure your notifications, NotiMind needs permission to read them. Click below to open settings, find 'NotiMind Lite' in the list, and toggle it ON.",
                        actionLabel = "Open Notification Settings",
                        icon = Icons.Default.NotificationsActive,
                        onAction = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    3 -> PermissionPage(
                        title = "Step 2: Direct Boot",
                        description = "To ensure your notifications are recovered even after a phone reboot (before you unlock), enable 'Direct Boot' in the app settings. This keeps the backup engine running in the background.",
                        actionLabel = "Check Settings",
                        icon = Icons.Default.History,
                        onAction = {
                            // In a real app, this would navigate to a specific settings toggle
                            // For this flow, we'll simulate it or open app info
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse(\"package:\${context.packageName}\")
                            }
                            context.startActivity(intent)
                        }
                    )
                    4 -> ChecklistPage(
                        items = listOf(
                            "Notification Access Enabled" to notificationAccessGranted,
                            "Direct Boot Configured" to directBootConfigured,
                            "Backup Engine Active" to true,
                            "Privacy Shield Enabled" to true
                        ),
                        onToggle = { item ->
                            if (item == "Notification Access Enabled") notificationAccessGranted = !notificationAccessGranted
                            if (item == "Direct Boot Configured") directBootConfigured = !directBootConfigured
                        }
                    )
                    5 -> FinalPage(
                        onFinish = {
                            preferenceManager.setOnboardingCompleted(true)
                            onCompleted()
                        }
                    )
                }
            }

            // Pager Indicator
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp)
                            .background(
                                if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            // Navigation Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Text("Back")
                }

                Button(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage < 5) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage < 5
                ) {
                    Text(if (pagerState.currentPage == 5) "Finish" else "Next")
                }
            }
        }
    }
}

@Composable
fun ConceptPage(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(24.dp),
            color = color
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PermissionPage(title: String, description: String, actionLabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
fun ChecklistPage(items: List<Pair<String, Boolean>>, onToggle: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = \"Ready to Go?\", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = \"Complete these for maximum reliability\", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { (text, checked) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(text) }
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(text) })
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
fun FinalPage(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = \"All Set!\", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            text = \"You're now insured against notification loss. Welcome to NotiMind Lite.\",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(\"Start Insuring Notifications\")
        }
    }
}
