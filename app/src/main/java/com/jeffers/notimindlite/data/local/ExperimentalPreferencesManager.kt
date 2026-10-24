package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExperimentalPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)

    private val _featureFlagsUrl = MutableStateFlow(preferences.getString(KEY_FEATURE_FLAGS_URL, "") ?: "")
    private val _mockDataEnabled = MutableStateFlow(preferences.getBoolean(KEY_MOCK_DATA_ENABLED, false))
    private val _testModeFlags = MutableStateFlow(preferences.getString(KEY_TEST_MODE_FLAGS, "") ?: "")
    private val _offloadEmbeddings = MutableStateFlow(preferences.getBoolean(KEY_OFFLOAD_EMBEDDINGS, false))
    private val _useFts5 = MutableStateFlow(preferences.getBoolean(KEY_USE_FTS5, false))
    private val _vectorGpu = MutableStateFlow(preferences.getBoolean(KEY_VECTOR_GPU, false))

    val featureFlagsUrl: StateFlow<String> = _featureFlagsUrl.asStateFlow()
    val mockDataEnabled: StateFlow<Boolean> = _mockDataEnabled.asStateFlow()
    val testModeFlags: StateFlow<String> = _testModeFlags.asStateFlow()
    val offloadEmbeddings: StateFlow<Boolean> = _offloadEmbeddings.asStateFlow()
    val useFts5: StateFlow<Boolean> = _useFts5.asStateFlow()
    val vectorGpu: StateFlow<Boolean> = _vectorGpu.asStateFlow()

    fun reload() {
        _featureFlagsUrl.value = preferences.getString(KEY_FEATURE_FLAGS_URL, "") ?: ""
        _mockDataEnabled.value = preferences.getBoolean(KEY_MOCK_DATA_ENABLED, false)
        _testModeFlags.value = preferences.getString(KEY_TEST_MODE_FLAGS, "") ?: ""
        _offloadEmbeddings.value = preferences.getBoolean(KEY_OFFLOAD_EMBEDDINGS, false)
        _useFts5.value = preferences.getBoolean(KEY_USE_FTS5, false)
        _vectorGpu.value = preferences.getBoolean(KEY_VECTOR_GPU, false)
    }

    fun setFeatureFlagsUrl(value: String) {
        val safe = value.trim().take(MAX_FEATURE_FLAGS_URL_LENGTH)
        preferences.edit().putString(KEY_FEATURE_FLAGS_URL, safe).apply()
        _featureFlagsUrl.value = safe
    }

    fun setMockDataEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_MOCK_DATA_ENABLED, value).apply()
        _mockDataEnabled.value = value
    }

    fun setTestModeFlags(value: String) {
        val safe = value.trim().take(MAX_TEST_MODE_FLAGS_LENGTH)
        preferences.edit().putString(KEY_TEST_MODE_FLAGS, safe).apply()
        _testModeFlags.value = safe
    }

    fun setOffloadEmbeddings(value: Boolean) {
        preferences.edit().putBoolean(KEY_OFFLOAD_EMBEDDINGS, value).apply()
        _offloadEmbeddings.value = value
    }

    fun setUseFts5(value: Boolean) {
        preferences.edit().putBoolean(KEY_USE_FTS5, value).apply()
        _useFts5.value = value
    }

    fun setVectorGpu(value: Boolean) {
        preferences.edit().putBoolean(KEY_VECTOR_GPU, value).apply()
        _vectorGpu.value = value
    }

    private companion object {
        const val MAX_FEATURE_FLAGS_URL_LENGTH = 2048
        const val MAX_TEST_MODE_FLAGS_LENGTH = 4096
        const val KEY_FEATURE_FLAGS_URL = "config_feature_flags_url"
        const val KEY_MOCK_DATA_ENABLED = "config_mock_data_enabled"
        const val KEY_TEST_MODE_FLAGS = "config_test_mode_flags"
        const val KEY_OFFLOAD_EMBEDDINGS = "config_offload_embeddings"
        const val KEY_USE_FTS5 = "config_use_fts5"
        const val KEY_VECTOR_GPU = "config_vector_gpu"
    }
}
