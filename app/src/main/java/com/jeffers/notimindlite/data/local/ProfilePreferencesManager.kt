package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProfilePreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(PROFILE_STORE, Context.MODE_PRIVATE)
    private var activeId = store.getString(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID
    private val _profiles = MutableStateFlow(readProfiles())
    private val _activeProfileId = MutableStateFlow(activeId)

    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()
    val activePreferencesName: String get() = "notimind_lite_prefs_$activeId"

    fun createProfile(name: String): Profile {
        val clean = name.trim().take(MAX_PROFILE_NAME_LENGTH)
            .ifBlank { "Profile ${_profiles.value.size + 1}" }
        val profile = Profile(UUID.randomUUID().toString(), clean, System.currentTimeMillis())
        writeProfiles((_profiles.value + profile).distinctBy { it.id })
        return profile
    }

    fun deleteProfile(id: String): Boolean {
        val updated = _profiles.value.filterNot { it.id == id }
        val canDelete = id != DEFAULT_PROFILE_ID && id != activeId
        val shouldDelete = canDelete && updated.size != _profiles.value.size
        if (shouldDelete) writeProfiles(updated)
        return shouldDelete
    }

    fun setActiveProfile(id: String): Boolean {
        if (_profiles.value.none { it.id == id }) return false
        activeId = id
        store.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
        _activeProfileId.value = id
        return true
    }

    fun exportProfile(id: String = activeId): String {
        require(_profiles.value.any { it.id == id }) { "Unknown profile" }
        val source = appContext.getSharedPreferences("notimind_lite_prefs_$id", Context.MODE_PRIVATE)
        val settings = JSONObject()
        source.all.forEach { (key, value) ->
            when (value) {
                is Boolean, is Int, is Long, is String -> settings.put(key, value)
            }
        }
        val profile = _profiles.value.first { it.id == id }
        return JSONObject()
            .put("version", PROFILE_EXPORT_VERSION)
            .put("profile", JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("createdAt", profile.createdAt))
            .put("settings", settings)
            .toString()
    }

    fun importProfile(serialized: String): Profile {
        val root = JSONObject(serialized)
        require(root.optInt("version", 0) == PROFILE_EXPORT_VERSION) { "Unsupported profile export" }
        val source = root.getJSONObject("profile")
        val id = source.optString("id")
            .takeIf { it.matches(PROFILE_ID) }
            ?.takeIf { candidate -> _profiles.value.none { it.id == candidate } }
            ?: UUID.randomUUID().toString()
        val profile = Profile(
            id,
            source.optString("name", "Imported profile").take(MAX_PROFILE_NAME_LENGTH),
            source.optLong("createdAt", System.currentTimeMillis())
        )
        val editor = appContext.getSharedPreferences("notimind_lite_prefs_$id", Context.MODE_PRIVATE).edit()
        root.optJSONObject("settings")?.keys()?.forEach { key ->
            when (val value = root.getJSONObject("settings").get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
        writeProfiles(_profiles.value + profile)
        return profile
    }

    private fun readProfiles(): List<Profile> {
        val raw = store.getString(KEY_PROFILES, null) ?: return createDefaultProfile()
        return parseProfiles(raw)
    }

    private fun createDefaultProfile(): List<Profile> {
        val profile = Profile(DEFAULT_PROFILE_ID, "Default", System.currentTimeMillis())
        store.edit().putString(KEY_PROFILES, encodeProfiles(listOf(profile))).apply()
        return listOf(profile)
    }

    private fun parseProfiles(raw: String): List<Profile> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            Profile(value.getString("id"), value.getString("name"), value.getLong("createdAt"))
        }
    }.getOrDefault(listOf(Profile(DEFAULT_PROFILE_ID, "Default", 0L)))

    private fun writeProfiles(value: List<Profile>) {
        val safe = if (value.any { it.id == DEFAULT_PROFILE_ID }) value
        else listOf(Profile(DEFAULT_PROFILE_ID, "Default", 0L)) + value
        store.edit().putString(KEY_PROFILES, encodeProfiles(safe)).apply()
        _profiles.value = safe
    }

    private fun encodeProfiles(profiles: List<Profile>): String = JSONArray(
        profiles.map { profile ->
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("createdAt", profile.createdAt)
        }
    ).toString()

    companion object {
        private const val PROFILE_STORE = "notimind_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val DEFAULT_PROFILE_ID = "default"
        private const val PROFILE_EXPORT_VERSION = 1
        private const val MAX_PROFILE_NAME_LENGTH = 80
        private val PROFILE_ID = Regex("[A-Za-z0-9-]{8,64}")
    }
}
