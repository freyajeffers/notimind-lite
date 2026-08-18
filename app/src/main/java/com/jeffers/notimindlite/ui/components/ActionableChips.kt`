package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.jeffers.notimindlite.util.ActionableEntityExtractor

/**
 * ActionableChips renders a row of interactive chips based on entities extracted from text.
 * Tapping a chip opens a contextual menu of actions.
 */
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

    val (icon, label, color) = when (entity.type) {
        ActionableEntityExtractor.EntityType.OTP -> 
            Triple(Icons.Default.ContentCopy, "OTP", MaterialTheme.colorScheme.primaryContainer)
        ActionableEntityExtractor.EntityType.URL -> 
            Triple(Icons.Default.OpenInNew, "Link", MaterialTheme.colorScheme.secondaryContainer)
    }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = color,
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${label}: ${entity.value}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Action 1: Copy (Always available)
            DropdownMenuItem(
                text = { Text("Copy ${if (entity.type == ActionableEntityExtractor.EntityType.OTP) "Code" else "Link"}") },
                onClick = {
                    clipboardManager.setText(entity.value)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )

            // Action 2: Specific Action (Open for URL)
            if (entity.type == ActionableEntityExtractor.EntityType.URL) {
                DropdownMenuItem(
                    text = { Text("Open in Browser") },
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entity.value)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handle failure silently or show toast
                        }
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Action 3: Share (Always available)
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
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
