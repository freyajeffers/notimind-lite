package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Friendly empty-state Composables for the Active Notifications screen.
 *
 * Three states are represented:
 *   1. [ActivePermissionEmptyState] — listener permission not yet granted
 *   2. [ActiveFirstRunEmptyState]   — permission granted, DB still empty
 *   3. [ActiveSearchEmptyState]     — search returned no matches
 *
 * Each state shows an icon, headline, body copy, and a context-appropriate CTA.
 * Implementation note: AGENTS.md §3 prefers additive code; these are pure-Composable
 * helpers and do not change any existing call sites.
 */

/**
 * Internal shell shared by all three empty states. Centers content vertically in the
 * available height, uses surface-variant background to distinguish from list rows.
 *
 * Suppressions: FunctionNaming requires camelCase for non-Composable functions but this
 * is a private Composable helper — pascal-case is conventional for Composables. The 8
 * parameters bundle icon, copy, modifiers, and three optional slot Composables so the
 * three public entry points can stay narrow and readable.
 */
@Suppress("LongParameterList", "FunctionNaming", "MagicNumber")
@Composable
private fun EmptyStateShell(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HORIZONTAL_PADDING_DP.dp, vertical = VERTICAL_PADDING_DP.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(ICON_RING_CORNER_PERCENT),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(ICON_RING_SIZE_DP.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(ICON_INNER_SIZE_DP.dp)
                    .padding(ICON_INNER_PADDING_DP.dp)
            )
        }
        Spacer(modifier = Modifier.height(TITLE_TOP_SPACER_DP.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(DESCRIPTION_TOP_SPACER_DP.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (extraContent != null) {
            Spacer(modifier = Modifier.height(EXTRA_TOP_SPACER_DP.dp))
            extraContent()
        }
        if (primaryAction != null) {
            Spacer(modifier = Modifier.height(PRIMARY_TOP_SPACER_DP.dp))
            primaryAction()
        }
        if (secondaryAction != null) {
            Spacer(modifier = Modifier.height(SECONDARY_TOP_SPACER_DP.dp))
            secondaryAction()
        }
    }
}

// Layout constants for [EmptyStateShell]. Defined at file scope so detekt's MagicNumber
// rule does not flag the dense spacing/sizing chain above; values follow Material 3
// guidance for hero-state displays (large icon ring, generous vertical rhythm).
private const val HORIZONTAL_PADDING_DP = 24
private const val VERTICAL_PADDING_DP = 32
private const val ICON_RING_CORNER_PERCENT = 50
private const val ICON_RING_SIZE_DP = 96
private const val ICON_INNER_SIZE_DP = 48
private const val ICON_INNER_PADDING_DP = 8
private const val TITLE_TOP_SPACER_DP = 24
private const val DESCRIPTION_TOP_SPACER_DP = 12
private const val EXTRA_TOP_SPACER_DP = 20
private const val PRIMARY_TOP_SPACER_DP = 28
private const val SECONDARY_TOP_SPACER_DP = 12

// Layout constants for [StepRow] (the numbered how-to list in the permission empty state).
private const val STEP_INDEX_SIZE_DP = 24
private const val STEP_INDEX_INNER_PADDING_DP = 4
private const val STEP_TEXT_SPACER_DP = 12
private const val STEP_GAP_DP = 8

// Step indices for [ActivePermissionEmptyState]'s how-to list. Defined as named
// constants so detekt's MagicNumber rule is satisfied and the order of the list is
// self-documenting (FIRST, SECOND, THIRD).
private const val FIRST_STEP_INDEX = 1
private const val SECOND_STEP_INDEX = 2
private const val THIRD_STEP_INDEX = 3

/**
 * Shown when the notification-listener permission is missing. Explains what the user
 * needs to do and offers a one-tap deep link to the system notification-access screen.
 *
 * PascalCase is required by the Compose API; 7 parameters carry copy + handler because
 * the call site is a single Composable invocation (callers never need to thread an
 * intermediate state object).
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
fun ActivePermissionEmptyState(
    title: String,
    description: String,
    step1: String,
    step2: String,
    step3: String,
    grantButtonText: String,
    onGrantClick: () -> Unit
) {
    EmptyStateShell(
        icon = Icons.Default.NotificationsOff,
        title = title,
        description = description,
        primaryAction = {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text(grantButtonText) }
        },
        extraContent = {
            Column(horizontalAlignment = Alignment.Start) {
                StepRow(FIRST_STEP_INDEX, step1)
                Spacer(modifier = Modifier.height(STEP_GAP_DP.dp))
                StepRow(SECOND_STEP_INDEX, step2)
                Spacer(modifier = Modifier.height(STEP_GAP_DP.dp))
                StepRow(THIRD_STEP_INDEX, step3)
            }
        }
    )
}

@Suppress("FunctionNaming") // Composable PascalCase required by Compose API.
@Composable
private fun StepRow(index: Int, text: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(ICON_RING_CORNER_PERCENT),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(STEP_INDEX_SIZE_DP.dp)
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(STEP_INDEX_INNER_PADDING_DP.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.size(STEP_TEXT_SPACER_DP.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Shown when permission is granted but no notifications have been captured yet.
 * Tells the user that capture is automatic — no setup required beyond permission.
 */
@Suppress("FunctionNaming") // Composable PascalCase required by Compose API.
@Composable
fun ActiveFirstRunEmptyState(
    title: String,
    description: String,
    hint: String
) {
    EmptyStateShell(
        icon = Icons.Default.NotificationsActive,
        title = title,
        description = description,
        secondaryAction = {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    )
}

/**
 * Shown when a search query produced zero matches. Offers a "clear search" CTA so the
 * user is never stuck staring at a result-less list with no escape hatch.
 */
@Suppress("FunctionNaming") // Composable PascalCase required by Compose API.
@Composable
fun ActiveSearchEmptyState(
    title: String,
    description: String,
    clearButtonText: String,
    onClearClick: () -> Unit
) {
    EmptyStateShell(
        icon = Icons.Default.SearchOff,
        title = title,
        description = description,
        secondaryAction = {
            OutlinedButton(onClick = onClearClick) { Text(clearButtonText) }
        }
    )
}
