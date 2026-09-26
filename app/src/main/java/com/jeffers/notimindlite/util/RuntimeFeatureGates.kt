package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.PreferencesRepository

/** Values read from PreferencesRepository, kept separate so gating is easy to unit test. */
data class ExperimentalFeaturePreferences(
    val mockDataEnabled: Boolean = false,
    val offloadEmbeddings: Boolean = false,
    val useFts5: Boolean = false,
    val vectorGpu: Boolean = false
)

/** Central runtime gates for experimental capabilities. All capabilities are opt-in twice:
 * the local preference and the remotely/test enabled flag must both allow the capability. */
object RuntimeFeatureGates {
    fun from(preferences: PreferencesRepository): ExperimentalFeaturePreferences = ExperimentalFeaturePreferences(
        mockDataEnabled = preferences.mockDataEnabled.value,
        offloadEmbeddings = preferences.offloadEmbeddings.value,
        useFts5 = preferences.useFts5.value,
        vectorGpu = preferences.vectorGpu.value
    )

    fun mockDataEnabled(preferences: ExperimentalFeaturePreferences, flags: FeatureFlagLoader): Boolean =
        preferences.mockDataEnabled && flags.isEnabled(FeatureFlag.MOCK_DATA)

    fun offloadEmbeddingsEnabled(preferences: ExperimentalFeaturePreferences, flags: FeatureFlagLoader): Boolean =
        preferences.offloadEmbeddings && flags.isEnabled(FeatureFlag.OFFLOAD_EMBEDDINGS)

    fun useFts5(preferences: ExperimentalFeaturePreferences, flags: FeatureFlagLoader): Boolean =
        preferences.useFts5 && flags.isEnabled(FeatureFlag.FTS5)

    fun vectorGpuEnabled(preferences: ExperimentalFeaturePreferences, flags: FeatureFlagLoader): Boolean =
        preferences.vectorGpu && flags.isEnabled(FeatureFlag.VECTOR_GPU)
}
