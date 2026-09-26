package com.jeffers.notimindlite.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.data.local.PreferencesRepository

/** Compose-only profile manager. Each profile has independent settings and Room storage. */
@Composable
fun ProfileManagerSection(repository: PreferencesRepository) {
    val context = LocalContext.current
    val profiles by repository.profiles.collectAsState()
    val activeId by repository.activeProfileId.collectAsState()
    var newName by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(repository.exportProfile().toByteArray()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { repository.importProfile(it.readText()) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profiles")
            profiles.forEach { profile ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (profile.id == activeId) "✓ ${profile.name}" else profile.name)
                    if (profile.id != activeId) OutlinedButton(onClick = { repository.setActiveProfile(profile.id) }) { Text("Use") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New profile") })
                Button(onClick = { repository.createProfile(newName); newName = "" }, enabled = newName.isNotBlank()) { Text("Add") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("notimind-profile.json") }) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Import") }
            }
        }
    }
}