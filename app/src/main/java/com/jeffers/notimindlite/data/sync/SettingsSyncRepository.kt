package com.jeffers.notimindlite.data.sync

import com.jeffers.notimindlite.data.local.PreferencesRepository

interface SettingsSyncRepository {
    suspend fun restoreOrUpload(preferences: PreferencesRepository, userId: String): Result<Unit>
}
