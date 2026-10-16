package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R

@Composable
fun displaySettingsSection(preferencesRepository: PreferencesRepository) {
    val compactMode by preferencesRepository.compactMode.collectAsState(initial = false)
    val showAppIcons by preferencesRepository.showAppIcons.collectAsState(initial = true)
    val groupByApp by preferencesRepository.groupByApp.collectAsState(initial = true)
    val sortOrder by preferencesRepository.sortOrder.collectAsState(initial = "newest")
    val previewLength by preferencesRepository.previewLength.collectAsState(initial = 140)
    val themeAccent by preferencesRepository.themeAccent.collectAsState(initial = "system")

    // Local option lists split across multiple lines to satisfy MaxLineLength rules
    val sortOptions = listOf(
        "newest" to stringResource(R.string.pref_sort_newest),
        "oldest" to stringResource(R.string.pref_sort_oldest),
        "app" to stringResource(R.string.pref_sort_app)
    )

    val themeOptions = listOf(
        "system" to stringResource(R.string.pref_theme_system),
        "blue" to stringResource(R.string.pref_theme_blue),
        "green" to stringResource(R.string.pref_theme_green),
        "purple" to stringResource(R.string.pref_theme_purple),
        "orange" to stringResource(R.string.pref_theme_orange),
        "amoled" to "AMOLED black"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
        Text(stringResource(R.string.pref_display_title), style = MaterialTheme.typography.titleMedium)

        preferenceSwitchRow(
            stringResource(R.string.pref_compact_mode_title),
            stringResource(R.string.pref_compact_mode_summary), compactMode,
            preferencesRepository::setCompactMode
        )
        preferenceSwitchRow(
            stringResource(R.string.pref_show_app_icons_title),
            stringResource(R.string.pref_show_app_icons_summary), showAppIcons,
            preferencesRepository::setShowAppIcons
        )
        preferenceSwitchRow(
            stringResource(R.string.pref_group_by_app_title),
            stringResource(R.string.pref_group_by_app_summary), groupByApp,
            preferencesRepository::setGroupByApp
        )
        preferenceSelectRow(
            stringResource(R.string.pref_sort_order_title),
            sortOrder,
            sortOptions,
            preferencesRepository::setSortOrder
        )
        preferenceSelectRow(
            stringResource(R.string.pref_theme_accent_title),
            themeAccent,
            themeOptions,
            preferencesRepository::setThemeAccent
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                Text(stringResource(R.string.pref_preview_length_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.pref_preview_length_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = previewLength.toString(),
                onValueChange = { it.toIntOrNull()?.let(preferencesRepository::setPreviewLength) },
                modifier = Modifier.width(110.dp), singleLine = true,
                label = { Text(stringResource(R.string.pref_preview_length_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}
