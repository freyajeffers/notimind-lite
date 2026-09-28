package com.jeffers.notimindlite.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagLoaderTest {
    @Test
    fun `remote flags enable only true values`() {
        val loader = FeatureFlagLoader().also {
            it.load("{\"flags\":{\"use_fts5\":true,\"vector_gpu\":false}}")
        }
        assertTrue(loader.isEnabled(FeatureFlag.FTS5))
        assertFalse(loader.isEnabled(FeatureFlag.VECTOR_GPU))
    }

    @Test
    fun `test mode flags override remote disabled values`() {
        val loader = FeatureFlagLoader().also {
            it.load("{\"offload_embeddings\":false}")
            it.setTestModeFlags("offload_embeddings, vector_gpu")
        }
        assertTrue(loader.isEnabled(FeatureFlag.OFFLOAD_EMBEDDINGS))
        assertTrue(loader.isEnabled(FeatureFlag.VECTOR_GPU))
    }

    @Test
    fun `invalid documents fail closed`() {
        val loader = FeatureFlagLoader()
        loader.load("not-json")
        assertFalse(loader.isEnabled(FeatureFlag.FTS5))
    }

    @Test
    fun `runtime gate requires local preference and flag`() {
        val loader = FeatureFlagLoader().also { it.load("{\"use_fts5\":true}") }
        assertTrue(RuntimeFeatureGates.useFts5(ExperimentalFeaturePreferences(useFts5 = true), loader))
        assertFalse(RuntimeFeatureGates.useFts5(ExperimentalFeaturePreferences(useFts5 = false), loader))
        assertFalse(RuntimeFeatureGates.vectorGpuEnabled(ExperimentalFeaturePreferences(vectorGpu = true), loader))
    }

    @Test
    fun `all experimental gates are independently evaluated`() {
        val loader = FeatureFlagLoader().also {
            it.setTestModeFlags("mock_data_enabled,offload_embeddings,vector_gpu")
        }
        val preferences = ExperimentalFeaturePreferences(
            mockDataEnabled = true,
            offloadEmbeddings = true,
            useFts5 = true,
            vectorGpu = true
        )
        assertTrue(RuntimeFeatureGates.mockDataEnabled(preferences, loader))
        assertTrue(RuntimeFeatureGates.offloadEmbeddingsEnabled(preferences, loader))
        assertFalse(RuntimeFeatureGates.useFts5(preferences, loader))
        assertTrue(RuntimeFeatureGates.vectorGpuEnabled(preferences, loader))
    }
}
