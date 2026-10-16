package com.jeffers.notimindlite.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.R

/**
 * SpeedDialSettingsFab provides a primary action button that expands into
 * a set of quick-action sub-buttons (Sync, Backup, Settings).
 */
@Composable
fun speedDialSettingsFab(
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = animationSpec,
        label = "fabScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = animationSpec,
        label = "fabRotation"
    )

    val quickActionStateDescription = stringResource(
        if (expanded) R.string.quick_actions_expanded else R.string.quick_actions_collapsed
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        if (expanded || scale > 0f) {
            speedDialActions(
                scale = scale,
                close = { expanded = false },
                onSyncClick = onSyncClick,
                onBackupClick = onBackupClick,
                onSettingsClick = onSettingsClick
            )
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.rotate(rotation),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            if (expanded) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.quick_actions_close),
                    modifier = Modifier.semantics { stateDescription = quickActionStateDescription }
                )
            } else {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.quick_actions_open),
                    modifier = Modifier.semantics { stateDescription = quickActionStateDescription }
                )
            }
        }
    }
}

@Composable
private fun speedDialActions(
    scale: Float,
    close: () -> Unit,
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(bottom = 72.dp)
    ) {
        speedDialItem(Icons.Default.Sync, stringResource(R.string.quick_actions_sync), scale) {
            close()
            onSyncClick()
        }
        speedDialItem(Icons.Default.Backup, stringResource(R.string.quick_actions_backup), scale) {
            close()
            onBackupClick()
        }
        speedDialItem(Icons.Default.Settings, stringResource(R.string.quick_actions_settings), scale) {
            close()
            onSettingsClick()
        }
    }
}

@Composable
private fun speedDialItem(
    icon: ImageVector,
    label: String,
    scale: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .scale(scale),
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
