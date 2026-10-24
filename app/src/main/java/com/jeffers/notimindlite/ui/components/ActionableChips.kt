package com.jeffers.notimindlite.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.domain.entity.ActionableEntityExtractor

@Composable
fun ActionableChips(
    text: String,
    modifier: Modifier = Modifier
) {
    val entities = ActionableEntityExtractor.extract(text)
    if (entities.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        entities.forEach { entity ->
            actionChip(entity = entity)
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ActionChip(
    entity: ActionableEntityExtractor.ActionableEntity
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val config = actionChipConfig(entity)

    val actionDescription = stringResource(
        R.string.actionable_action_suffix,
        config.label,
        entity.value
    )
    val menuStateDescription = stringResource(
        if (expanded) R.string.actionable_menu_open else R.string.actionable_menu_closed
    )
    val shareViaLabel = stringResource(R.string.actionable_share_via)

    Box {
        AssistChip(
            onClick = {
                expanded = true
            },
            label = {
                Text(
                    text = stringResource(R.string.actionable_chip_label, config.label, entity.value),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = config.onColor
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = config.icon,
                    contentDescription = config.label,
                    modifier = Modifier.size(14.dp),
                    tint = config.onColor
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = config.containerColor,
                labelColor = config.onColor,
                leadingIconContentColor = config.onColor
            ),
            modifier = Modifier.semantics {
                contentDescription = actionDescription
                stateDescription = menuStateDescription
            }
        )

        actionChipMenu(
            ActionChipMenuParams(
                entity = entity,
                expanded = expanded,
                context = context,
                shareViaLabel = shareViaLabel,
                onDismiss = { expanded = false }
            )
        )
    }
}

@Composable
private fun actionChipConfig(
    entity: ActionableEntityExtractor.ActionableEntity
): ActionChipConfig = when (entity.type) {
    ActionableEntityExtractor.EntityType.OTP -> ActionChipConfig(
        Icons.Default.ContentCopy,
        stringResource(R.string.actionable_entity_otp),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )

    ActionableEntityExtractor.EntityType.URL -> ActionChipConfig(
        icon = Icons.AutoMirrored.Filled.OpenInNew,
        label = stringResource(R.string.actionable_entity_link),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    ActionableEntityExtractor.EntityType.LOCATION -> ActionChipConfig(
        Icons.Default.Place,
        stringResource(R.string.actionable_entity_place),
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
}

@Composable
private fun ActionChipMenu(params: ActionChipMenuParams) {
    val entity = params.entity
    val expanded = params.expanded
    val context = params.context
    val shareViaLabel = params.shareViaLabel
    val onDismiss = params.onDismiss
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(menuLabelForCopy(entity)) },
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as?
                    ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("Actionable Content", entity.value)
                )
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
            }
        )
        val canOpenExternally = entity.type == ActionableEntityExtractor.EntityType.URL ||
            entity.type == ActionableEntityExtractor.EntityType.LOCATION
        if (canOpenExternally) {
            externalActionMenuItem(entity, context, onDismiss)
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.actionable_share)) },
            onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, entity.value)
                }
                context.startActivity(Intent.createChooser(shareIntent, shareViaLabel))
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.actionable_share),
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun ExternalActionMenuItem(
    entity: ActionableEntityExtractor.ActionableEntity,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                if (entity.type == ActionableEntityExtractor.EntityType.URL) {
                    stringResource(R.string.actionable_open_browser)
                } else {
                    stringResource(R.string.actionable_open_maps)
                }
            )
        },
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, externalUriFor(entity).toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // No compatible external handler is installed.
            }
            onDismiss()
        },
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open",
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

private data class ActionChipMenuParams(
    val entity: ActionableEntityExtractor.ActionableEntity,
    val expanded: Boolean,
    val context: Context,
    val shareViaLabel: String,
    val onDismiss: () -> Unit
)

data class ActionChipConfig(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val onColor: Color
)

@Composable
private fun menuLabelForCopy(
    entity: ActionableEntityExtractor.ActionableEntity
): String = when (entity.type) {
    ActionableEntityExtractor.EntityType.OTP -> stringResource(R.string.actionable_copy_code)
    ActionableEntityExtractor.EntityType.URL -> stringResource(R.string.actionable_copy_link)
    ActionableEntityExtractor.EntityType.LOCATION -> stringResource(R.string.actionable_copy_address)
}

private fun externalUriFor(
    entity: ActionableEntityExtractor.ActionableEntity
): String = if (entity.type == ActionableEntityExtractor.EntityType.URL) {
    entity.value
} else {
    "geo:0,0?q=${Uri.encode(entity.value)}"
}
