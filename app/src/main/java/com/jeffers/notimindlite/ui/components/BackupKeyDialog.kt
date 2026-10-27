package com.jeffers.notimindlite.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeffers.notimindlite.R
import kotlinx.coroutines.delay

private const val CLIPBOARD_CLEAR_DELAY_MILLIS = 30_000L

private fun clearBackupKeyClipboard(context: Context, keyBase64: String, notify: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard?.primaryClip?.itemCount == 1 &&
        clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString() == keyBase64
    ) {
        clipboard.clearPrimaryClip()
        if (notify) {
            android.widget.Toast
                .makeText(
                    context,
                    R.string.backup_dialog_copied_key_cleared,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
        }
    }
}

@Composable
private fun ClearCopiedBackupKeyFromClipboard(
    context: Context,
    keyBase64: String,
    clipboardKeyCopied: Boolean,
    onCleared: () -> Unit,
) {
    LaunchedEffect(clipboardKeyCopied, keyBase64) {
        if (!clipboardKeyCopied) return@LaunchedEffect
        delay(CLIPBOARD_CLEAR_DELAY_MILLIS)
        clearBackupKeyClipboard(context, keyBase64, notify = true)
        onCleared()
    }
}

@Composable
private fun BackupKeyActions(
    context: Context,
    keyBase64: String,
    shareKeyPayload: String,
    shareKeyChooser: String,
    onCopied: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = keyBase64,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Backup Key", keyBase64))
                onCopied(clipboard != null)
            }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.backup_dialog_copy_key),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    TextButton(
        onClick = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareKeyPayload)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, shareKeyChooser))
        },
        modifier = Modifier,
    ) {
        Icon(Icons.Default.Share, contentDescription = "Share backup key", modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.backup_key_share))
    }
}

@Composable
fun BackupKeyDialog(
    keyBase64: String,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray?) -> Unit,
) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var clipboardKeyCopied by remember { mutableStateOf(false) }
    val shareKeyPayload = stringResource(R.string.backup_share_key_payload, keyBase64)
    val shareKeyChooser = stringResource(R.string.backup_share_key_chooser)

    ClearCopiedBackupKeyFromClipboard(
        context = context,
        keyBase64 = keyBase64,
        clipboardKeyCopied = clipboardKeyCopied,
        onCleared = { clipboardKeyCopied = false },
    )

    AlertDialog(
        onDismissRequest = {
            clearBackupKeyClipboard(context, keyBase64, notify = false)
            onDismiss()
        },
        title = {
            Text(
                stringResource(R.string.backup_dialog_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.backup_dialog_passphrase_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.backup_dialog_passphrase_label)) },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showPassword) {
                                        R.string.common_hide_passphrase
                                    } else {
                                        R.string.common_show_passphrase
                                    }
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.backup_dialog_local_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.backup_dialog_key_sharing_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                BackupKeyActions(
                    context = context,
                    keyBase64 = keyBase64,
                    shareKeyPayload = shareKeyPayload,
                    shareKeyChooser = shareKeyChooser,
                    onCopied = { clipboardKeyCopied = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clearBackupKeyClipboard(context, keyBase64, notify = false)
                    val pass = if (passphrase.isBlank()) null else passphrase.toCharArray()
                    onConfirm(pass)
                },
            ) {
                Text(stringResource(R.string.backup_dialog_create))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clearBackupKeyClipboard(context, keyBase64, notify = false)
                onDismiss()
            }) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
