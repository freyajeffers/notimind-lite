package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.R
import kotlinx.coroutines.launch

@Composable
fun settingsPrivacySection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val autoDelete by preferencesRepository.autoDeleteOnRead.collectAsState(initial = false)
    val anonymizeTitles by preferencesRepository.anonymizeTitles.collectAsState(initial = false)
    val redactPii by preferencesRepository.securityManager.redactPii.collectAsState(initial = false)
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    var strictPrivacyEnabled by remember { mutableStateOf(preferenceManager.isStrictPrivacyEnabled()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_privacy_title), style = MaterialTheme.typography.titleMedium)

            val autoDeleteTitle = stringResource(R.string.pref_auto_delete_title)
            val autoDeleteSummary = stringResource(R.string.pref_auto_delete_summary)
            preferenceToggleRow(
                title = autoDeleteTitle,
                summary = autoDeleteSummary,
                checked = autoDelete,
                onChanged = { scope.launch { preferencesRepository.setAutoDeleteOnRead(it) } },
                enabled = !BuildConfig.DEBUG
            )

            val anonymizeTitle = stringResource(R.string.pref_anonymize_title)
            val anonymizeSummary = stringResource(R.string.pref_anonymize_summary)
            preferenceToggleRow(
                title = anonymizeTitle,
                summary = anonymizeSummary,
                checked = anonymizeTitles,
                onChanged = { scope.launch { preferencesRepository.setAnonymizeTitles(it) } },
                enabled = !BuildConfig.DEBUG
            )

            val redactTitle = stringResource(R.string.pref_redact_pii_title)
            val redactSummary = stringResource(R.string.pref_redact_pii_summary)
            preferenceToggleRow(
                title = redactTitle,
                summary = redactSummary,
                checked = redactPii,
                onChanged = { scope.launch { preferencesRepository.securityManager.setRedactPii(it) } },
                enabled = !BuildConfig.DEBUG
            )

            strictPrivacyRow(
                enabled = strictPrivacyEnabled,
                onChanged = {
                    strictPrivacyEnabled = it
                    preferenceManager.setStrictPrivacyEnabled(it)
                }
            )
        }
    }
}

@Composable
private fun preferenceToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChanged, enabled = enabled)
    }
}

@Composable
private fun strictPrivacyRow(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    preferenceToggleRow(
        title = "Strict Privacy Mode",
        summary = "Disable all crash reporting and anonymous usage telemetry.",
        checked = enabled,
        onChanged = onChanged
    )
}
