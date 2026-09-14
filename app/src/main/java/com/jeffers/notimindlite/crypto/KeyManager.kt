package com.jeffers.notimindlite.crypto

import android.content.Context
import android.util.Log

/**
 * Lightweight shim for acquiring a MasterKey alias. We avoid a hard compile-time
 * dependency on androidx.security.crypto so JVM unit tests and CI that don't have
 * AndroidX on the classpath can still compile. At runtime on Android devices the
 * implementation will try to call AndroidX Security via reflection.
 */
object KeyManager {
    private const val TAG = "KeyManager"

    fun getOrCreateMasterKeyAlias(context: Context?, alias: String?): String {
        // Preferred runtime: androidx.security.crypto.MasterKeys.getOrCreate(...)
        try {
            val cls = Class.forName("androidx.security.crypto.MasterKeys")
            val field = cls.getField("AES256_GCM_SPEC")
            val spec = field.get(null)
            val method = cls.getMethod("getOrCreate", spec::class.java)
            val masterKeyAlias = method.invoke(null, spec) as? String
            if (!masterKeyAlias.isNullOrBlank()) return masterKeyAlias
        } catch (e: ClassNotFoundException) {
            // AndroidX Security not available on the JVM; fall back to a deterministic alias.
        } catch (e: Exception) {
            Log.w(TAG, "Reflection to MasterKeys failed, falling back to deterministic alias", e)
        }

        // Deterministic fallback alias (safe for testing; in production prefer AndroidX Security)
        return alias ?: "notimind_master_key"
    }
}
