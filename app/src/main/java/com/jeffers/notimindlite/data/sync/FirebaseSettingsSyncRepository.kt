package com.jeffers.notimindlite.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class FirebaseSettingsSyncRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SettingsSyncRepository {
    override suspend fun restoreOrUpload(preferences: PreferencesRepository, userId: String): Result<Unit> = try {
        val ref = firestore
            .collection("users")
            .document(userId)
            .collection("settings")
            .document("preferences")
        val remote = ref.get().await()
        val local = preferences.settingsSyncManager.exportSyncableSettings()
        if (!remote.exists()) {
            ref.set(mapOf("settings" to local.toString(), "updatedAt" to System.currentTimeMillis())).await()
        } else {
            val remoteSettings = JSONObject(remote.getString("settings") ?: "{}")
            preferences.settingsSyncManager.importSyncableSettings(remoteSettings)
            ref.set(mapOf("settings" to remoteSettings.toString(), "updatedAt" to System.currentTimeMillis())).await()
        }
        Result.success(Unit)
    } catch (e: IllegalStateException) {
        Result.failure(e)
    }
}
