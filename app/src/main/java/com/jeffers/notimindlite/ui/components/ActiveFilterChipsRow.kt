package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Horizontally-scrolling row of [AssistChip]s that summarise currently active filters
 * (selected packages, search query). Each chip has a close affordance; tapping the chip
 * itself clears only that filter. A trailing "Clear" text button removes all filters.
 *
 * Pure-Composable, additive — does not change any existing call sites.
 */
@Suppress("FunctionNaming") // Composable PascalCase required by Compose API.
@Composable
fun ActiveFilterChipsRow(
    label: String,
    chips: List<ActiveFilterChip>,
    clearAllLabel: String,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = CHIPS_ROW_H_PADDING_DP.dp, vertical = CHIPS_ROW_V_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        chips.forEach { chip ->
            AssistChip(
                onClick = { onClearAll(); chip.onClear() },
                label = { Text(chip.text) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove ${chip.text}",
                        modifier = Modifier.size(CHIP_TRAILING_ICON_DP.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Filter: ${chip.text}. Tap to remove."
                }
            )
        }
        if (chips.isNotEmpty()) {
            TextButton(onClick = onClearAll) {
                Text(clearAllLabel)
            }
        }
    }
}

// Layout constants for [ActiveFilterChipsRow]. Material 3 chip-row spec: 8.dp inter-chip
// gap, 16.dp horizontal page padding, 4.dp vertical, 16.dp trailing icon size.
private const val CHIPS_ROW_H_PADDING_DP = 16
private const val CHIPS_ROW_V_PADDING_DP = 4
private const val CHIP_GAP_DP = 8
private const val CHIP_TRAILING_ICON_DP = 16

/**
 * One filter chip shown by [ActiveFilterChipsRow]. [text] is the user-visible label;
 * [onClear] is invoked when the chip's trailing close icon is tapped (or when the row's
 * "Clear all" is tapped — the row also invokes onClear on every chip to keep behaviour
 * consistent regardless of entry point).
 */
data class ActiveFilterChip(
    val text: String,
    val onClear: () -> Unit
)
