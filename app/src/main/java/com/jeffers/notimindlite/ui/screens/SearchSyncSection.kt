package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository

@Composable
fun SearchSyncSection(preferencesRepository: PreferencesRepository) {
    val enableSync by preferencesRepository.syncManager.enableSync.collectAsState(initial = true)
    val enableVector by preferencesRepository.syncManager.enableVector.collectAsState(initial = true)
    val enableFts4 by preferencesRepository.syncManager.enableFts4.collectAsState(initial = true)
    val enableSemanticRanking by preferencesRepository.enableSemanticRanking.collectAsState(initial = true)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
        Text(stringResource(R.string.pref_data_search_title), style = MaterialTheme.typography.titleMedium)

        PreferenceSwitchRow(
            stringResource(R.string.pref_enable_sync_title),
            stringResource(R.string.pref_enable_sync_summary), enableSync,
            preferencesRepository.syncManager::setEnableSync
        )

        PreferenceSwitchRow(
            stringResource(R.string.pref_enable_vector_title),
            stringResource(R.string.pref_enable_vector_summary), enableVector,
            preferencesRepository.syncManager::setEnableVector
        )

        PreferenceSwitchRow(
            stringResource(R.string.pref_enable_fts4_title),
            stringResource(R.string.pref_enable_fts4_summary), enableFts4,
            preferencesRepository.syncManager::setEnableFts4
        )

        PreferenceSwitchRow(
            stringResource(R.string.pref_semantic_ranking_title),
            stringResource(R.string.pref_semantic_ranking_summary), enableSemanticRanking,
            preferencesRepository::setEnableSemanticRanking
        )
    }
}
