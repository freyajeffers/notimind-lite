package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import com.jeffers.notimindlite.util.ActionableEntityExtractor

/**
 * ActionableChips renders a row of interactive chips based on entities extracted from text.
 */
@Composable
fun ActionableChips(
    text: String,
    modifier: Modifier = Modifier
) {
    val entities = ActionableEntityExtractor.extract(text)
    if (entities.isEmpty()) return

    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        entities.forEach { entity ->
            ActionChip(
                entity = entity,
                onAction = {
                    when (entity.type) {
                        ActionableEntityExtractor.EntityType.OTP -> {
                            clipboardManager.setText(AnnotatedString(entity.value))
                        }
                        ActionableEntityExtractor.EntityType.URL -> {
                            // URL opening logic would go here
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ActionChip(
    entity: ActionableEntityExtractor.ActionableEntity,
    onAction: () -> Unit
) {
    val (icon, label, color) = when (entity.type) {
        ActionableEntityExtractor.EntityType.OTP -> 
            Triple(Icons.Default.ContentCopy, "Copy OTP", MaterialTheme.colorScheme.primaryContainer)
        ActionableEntityExtractor.EntityType.URL -> 
            Triple(Icons.Default.OpenInNew, "Open Link", MaterialTheme.colorScheme.secondaryContainer)
    }

    Surface(
        onClick = onAction,
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
                text = "$label: ${entity.value}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
