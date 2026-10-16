package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.DbEncryptionMode
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.BuildConfig

@Composable
fun settingsPreferencesSection(preferencesRepository: PreferencesRepository) {

    val enableSync by preferencesRepository.enableSync.collectAsState(initial = true)
    val enableVector by preferencesRepository.enableVector.collectAsState(initial = true)
    val enableFts4 by preferencesRepository.enableFts4.collectAsState(initial = true)
    val enableSemanticRanking by preferencesRepository.enableSemanticRanking.collectAsState(initial = true)
    val compactMode by preferencesRepository.compactMode.collectAsState(initial = false)
    val showAppIcons by preferencesRepository.showAppIcons.collectAsState(initial = true)
    val groupByApp by preferencesRepository.groupByApp.collectAsState(initial = true)
    val sortOrder by preferencesRepository.sortOrder.collectAsState(initial = "newest")
    val previewLength by preferencesRepository.previewLength.collectAsState(initial = 140)
    val themeAccent by preferencesRepository.themeAccent.collectAsState(initial = "system")
    val exportRequiresBiometric by preferencesRepository.exportRequiresBiometric.collectAsState(initial = false)
    val appLockEnabled by preferencesRepository.appLockEnabled.collectAsState(initial = false)
    val useKeystore by preferencesRepository.useKeystore.collectAsState(initial = true)
    val dbEncrypted by preferencesRepository.dbEncrypted.collectAsState(initial = true)
    val dbEncryptionMode by preferencesRepository.dbEncryptionMode.collectAsState(initial = DbEncryptionMode.KEYSTORE)
    val autoActOnNotification by preferencesRepository.autoActOnNotification.collectAsState(initial = false)
    val defaultReplyMethod by preferencesRepository.defaultReplyMethod.collectAsState(initial = "inline")
    val longPressAction by preferencesRepository.longPressAction.collectAsState(initial = "open")
    val autoExecuteRules by preferencesRepository.autoExecuteRules.collectAsState(initial = emptyList())
    var rulePackage by remember { mutableStateOf("") }
    var ruleTitle by remember { mutableStateOf("") }
    var ruleAction by remember { mutableStateOf("0") }

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

    val replyOptions = listOf(
        "inline" to stringResource(R.string.pref_reply_inline),
        "open_app" to stringResource(R.string.pref_reply_open_app),
        "copy" to stringResource(R.string.pref_reply_copy)
    )

    val longPressOptions = listOf(
        "open" to stringResource(R.string.pref_action_open),
        "reply" to stringResource(R.string.pref_action_reply),
        "dismiss" to stringResource(R.string.pref_action_dismiss)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_data_search_title), style = MaterialTheme.typography.titleMedium)

            displaySettingsSection(preferencesRepository)

            Text(stringResource(R.string.pref_automation_title), style = MaterialTheme.typography.titleMedium)
            preferenceSwitchRow(
                stringResource(R.string.pref_auto_act_title),
                stringResource(R.string.pref_auto_act_summary), autoActOnNotification,
                preferencesRepository::setAutoActOnNotification, enabled = !BuildConfig.DEBUG
            )
            preferenceSelectRow(
                stringResource(R.string.pref_default_reply_title),
                defaultReplyMethod,
                replyOptions,
                preferencesRepository::setDefaultReplyMethod,
                enabled = !BuildConfig.DEBUG
            )
            preferenceSelectRow(
                stringResource(R.string.pref_long_press_title),
                longPressAction,
                longPressOptions,
                preferencesRepository::setLongPressAction,
                enabled = !BuildConfig.DEBUG
            )
            autoRulesSection(
                preferencesRepository = preferencesRepository,
                config = AutoRulesConfig(
                    autoExecuteRules = autoExecuteRules,
                    rulePackage = rulePackage,
                    onRulePackageChange = { rulePackage = it },
                    ruleTitle = ruleTitle,
                    onRuleTitleChange = { ruleTitle = it },
                    ruleAction = ruleAction,
                    onRuleActionChange = { ruleAction = it }
                )
            )

            Text(stringResource(R.string.pref_security_local_title), style = MaterialTheme.typography.titleMedium)
            preferenceSwitchRow(
                stringResource(R.string.pref_export_biometric_title),
                stringResource(R.string.pref_export_biometric_summary), exportRequiresBiometric,
                preferencesRepository::setExportRequiresBiometric
            )
            preferenceSwitchRow(
                stringResource(R.string.pref_app_lock_title),
                stringResource(R.string.pref_app_lock_summary), appLockEnabled,
                preferencesRepository::setAppLockEnabled
            )
            preferenceSwitchRow(
                stringResource(R.string.pref_encrypt_database_title),
                stringResource(R.string.pref_encrypt_database_summary), dbEncrypted,
                preferencesRepository::setDbEncrypted
            )
            preferenceSelectRow(
                stringResource(R.string.pref_database_key_mode_title),
                dbEncryptionMode.persisted,
                listOf(
                    "none" to stringResource(R.string.pref_key_none),
                    "keystore" to stringResource(R.string.pref_key_keystore),
                    "local_key" to stringResource(R.string.pref_key_local)
                ),
                { preferencesRepository.setDbEncryptionMode(DbEncryptionMode.fromPersisted(it)) }
            )
            preferenceSwitchRow(
                stringResource(R.string.pref_keystore_title),
                stringResource(R.string.pref_keystore_summary), useKeystore,
                preferencesRepository::setUseKeystore, enabled = dbEncrypted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_sync_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.pref_enable_sync_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enableSync, onCheckedChange = preferencesRepository::setEnableSync)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_vector_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.pref_enable_vector_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enableVector, onCheckedChange = preferencesRepository::setEnableVector)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_fts4_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.pref_enable_fts4_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enableFts4, onCheckedChange = preferencesRepository::setEnableFts4)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_semantic_ranking_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.pref_semantic_ranking_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enableSemanticRanking, onCheckedChange = preferencesRepository::setEnableSemanticRanking)
            }

            debugSettingsSection(preferencesRepository)
        }
    }
}


@Composable
internal fun disabledPreferenceRow(title: String, summary: String) {
    preferenceSwitchRow(title, summary, checked = false, onCheckedChange = {}, enabled = false)
}

@Composable
internal fun preferenceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun preferenceSelectRow(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.semantics { contentDescription = title }) { Text(options.firstOrNull { it.first == selected }?.second ?: selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { onSelected(key); expanded = false })
                }
            }
        }
    }
}
