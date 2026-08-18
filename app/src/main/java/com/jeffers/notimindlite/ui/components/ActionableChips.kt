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
        ActionableEntityExtractor.EntityType.LOCATION -> 
            Triple(Icons.Default.Place, "Place", MaterialTheme.colorScheme.tertiaryContainer)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
