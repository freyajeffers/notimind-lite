package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R

@Composable
fun RestoreBackupDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray?) -> Unit,
) {
    var passphrase = remember { TextFieldState() }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.restore_dialog_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.restore_dialog_file, fileName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.restore_dialog_passphrase_description),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedSecureTextField(
                    state = passphrase,
                    label = { Text(stringResource(R.string.backup_dialog_passphrase_label)) },
                    textObfuscationMode = if (showPassword) {
                        TextObfuscationMode.Visible
                    } else {
                        TextObfuscationMode.Hidden
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
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val passphraseText = passphrase.text.toString()
                    val pass = if (passphraseText.isBlank()) null else passphraseText.toCharArray()
                    onConfirm(pass)
                },
            ) {
                Text(stringResource(R.string.restore_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
