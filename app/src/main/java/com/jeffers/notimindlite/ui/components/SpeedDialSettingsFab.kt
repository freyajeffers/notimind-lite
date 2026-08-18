package com.jeffers.notimindlite.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * SpeedDialSettingsFab provides a primary action button that expands into
 * a set of quick-action sub-buttons (Sync, Backup, Settings).
 */
@Composable
fun SpeedDialSettingsFab(
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val scale by animateFloatAsState(targetValue = if (expanded) 1f else 0f, animationSpec = animationSpec, label = "fabScale")
    val rotation by animateFloatAsState(targetValue = if (expanded) 45f else 0f, animationSpec = animationSpec, label = "fabRotation")

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        if (expanded || scale > 0f) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 72.dp)
            ) {
                SpeedDialItem(
                    icon = Icons.Default.Sync,
                    label = "Sync Now",
                    scale = scale,
                    onClick = {
                        expanded = false
                        onSyncClick()
                    }
                )
                SpeedDialItem(
                    icon = Icons.Default.Backup,
                    label = "Backup",
                    scale = scale,
                    onClick = {
                        expanded = false
                        onBackupClick()
                    }
                )
                SpeedDialItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    scale = scale,
                    onClick = {
                        expanded = false
                        onSettingsClick()
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.rotate(rotation),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            if (expanded) {
                Icon(Icons.Default.Close, contentDescription = "Close Menu")
            } else {
                Icon(Icons.Default.Settings, contentDescription = "Quick Actions")
            }
        }
    }
}

@Composable
private fun SpeedDialItem(
    icon: ImageVector,
    label: String,
    scale: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .scale(scale)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
