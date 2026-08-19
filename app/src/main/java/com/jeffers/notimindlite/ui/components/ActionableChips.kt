package com.jeffers.notimindlite.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.util.ActionableEntityExtractor
import com.jeffers.notimindlite.util.TelemetryManager

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
            ActionChip(entity = entity)
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
    val clipboardManager = LocalClipboardManager.current

    val (icon, label, color, onColor) = when (entity.type) {
        ActionableEntityExtractor.EntityType.OTP -> 
            listOf(Icons.Default.ContentCopy, "OTP", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ActionableEntityExtractor.EntityType.URL -> 
            listOf(Icons.Default.OpenInNew, "Link", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        ActionableEntityExtractor.EntityType.LOCATION -> 
            listOf(Icons.Default.Place, "Place", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }.let { 
        // Manually extracting from the list to avoid Tuple/Quadruple issues in the patch
        // Actually, I'll just define them as local variables for clarity.
        return@let Unit 
    }

    // Redoing the logic cleanly
    val config = when (entity.type) {
        ActionableEntityExtractor.EntityType.OTP -> 
            ActionChipConfig(Icons.Default.ContentCopy, "OTP", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ActionableEntityExtractor.EntityType.URL -> 
            ActionChipConfig(Icons.Default.OpenInNew, "Link", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        ActionableEntityExtractor.EntityType.LOCATION -> 
            ActionChipConfig(Icons.Default.Place, "Place", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }

    Box {
        AssistChip(
            onClick = { 
                                TelemetryManager.logFeatureUsage("chip_clicked", mapOf("entity_type" to entity.type.name))
                                expanded = true 
                            },
            label = { 
                Text(
                    text = "${config.label}: ${entity.value}",
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = config.onColor
                ) 
            },
            leadingIcon = {
                Icon(
                    imageVector = config.icon,
                    contentDescription = null, // Label is in the text
                    modifier = Modifier.size(14.dp),
                    tint = config.onColor
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = config.containerColor,
                labelColor = config.onColor,
                leadingIconColor = config.onColor
            ),
            modifier = Modifier.semantics { 
                contentDescription = "${config.label} action: ${entity.value}" 
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy ${if (entity.type == ActionableEntityExtractor.EntityType.OTP) "Code" else if (entity.type == ActionableEntityExtractor.EntityType.URL) "Link" else "Address"}") },
                onClick = {
                    clipboardManager.setText(AnnotatedString(entity.value))
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )

            if (entity.type == ActionableEntityExtractor.EntityType.URL || entity.type == ActionableEntityExtractor.EntityType.LOCATION) {
                DropdownMenuItem(
                    text = { Text(if (entity.type == ActionableEntityExtractor.EntityType.URL) "Open in Browser" else "Open in Maps") },
                    onClick = {
                        try {
                            val uriString = if (entity.type == ActionableEntityExtractor.EntityType.URL) entity.value else "geo:0,0?q=${Uri.encode(entity.value)}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, entity.value)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

data class ActionChipConfig(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val onColor: Color
)
