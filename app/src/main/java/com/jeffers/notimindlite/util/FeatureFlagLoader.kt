package com.jeffers.notimindlite.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun fetchFeatureFlagsUrl(url: String): String? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        requestMethod = "GET"
    }
    return try {
        if (connection.responseCode !in 200..299) null
        else connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** Names used by the remote feature-flags document and the test-mode override. */
enum class FeatureFlag(val key: String) {
    MOCK_DATA("mock_data_enabled"),
    OFFLOAD_EMBEDDINGS("offload_embeddings"),
    FTS5("use_fts5"),
    VECTOR_GPU("vector_gpu");

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Loads a small JSON feature-flag document. Network I/O is explicit and never occurs from
 * [isEnabled], which makes checks deterministic and safe to use from UI and database code.
 *
 * Accepted documents are either `{ "flags": { "use_fts5": true } }` or a flat object. Test
 * mode flags are a comma-separated list (or the same JSON object) and override remote values.
 */
class FeatureFlagLoader(private val fetch: (String) -> String? = ::fetchFeatureFlagsUrl) {
    @Volatile private var remoteFlags: Set<String> = emptySet()
    @Volatile private var testFlags: Set<String> = emptySet()

    fun load(json: String): Set<String> {
        val parsed = parseFlags(json)
        remoteFlags = parsed
        return parsed
    }

    fun loadFromUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val body = runCatching { fetch(url) }.getOrNull() ?: return false
        return runCatching { load(body); true }.getOrDefault(false)
    }

    fun setTestModeFlags(serialized: String) {
        testFlags = parseFlags(serialized, allowList = true)
    }

    fun isEnabled(flag: FeatureFlag): Boolean = isEnabled(flag.key)

    fun isEnabled(key: String): Boolean {
        val normalized = normalize(key)
        return normalized in testFlags || normalized in remoteFlags
    }

    fun enabledFlags(): Set<String> = (remoteFlags + testFlags).toSet()

    private fun parseFlags(serialized: String, allowList: Boolean = false): Set<String> {
        val trimmed = serialized.trim()
        if (trimmed.isEmpty()) return emptySet()
        if (allowList && !trimmed.startsWith("{")) {
            return trimmed.split(',', '\n', ';').map(::normalize).filter(String::isNotBlank).toSet()
        }
        return runCatching {
            val root = JSONObject(trimmed)
            val values = root.optJSONObject("flags") ?: root
            values.keys().asSequence().filter { values.optBoolean(it, false) }.map(::normalize).toSet()
        }.getOrDefault(emptySet())
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace('-', '_')

}
