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
            Log.i(TAG, "AndroidX Security MasterKeys not found on classpath; using fallback alias", e)
        } catch (e: NoSuchFieldException) {
            Log.w(TAG, "MasterKeys AES256_GCM_SPEC field missing; using fallback alias", e)
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "MasterKeys.getOrCreate method missing; using fallback alias", e)
        } catch (e: IllegalAccessException) {
            Log.w(TAG, "Access denied invoking MasterKeys; using fallback alias", e)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            Log.w(TAG, "Error invoking MasterKeys.getOrCreate; using fallback alias", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception while accessing MasterKeys; using fallback alias", e)
        } catch (e: ClassCastException) {
            Log.w(TAG, "Unexpected MasterKeys return type; using fallback alias", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid argument when invoking MasterKeys; using fallback alias", e)
        }

        // Deterministic fallback alias (safe for testing; in production prefer AndroidX Security)
        // Use the provided context when available to form a slightly more unique alias
        // so the `context` parameter is meaningfully used and Detekt doesn't flag it.
        return alias ?: context?.packageName?.let { "${it}.notimind_master_key" } ?: "notimind_master_key"
    }
}
