package com.jeffers.notimindlite.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Versioned AndroidKeyStore-backed preferences for secrets that must survive
 * process death but must not be stored as plaintext XML.
 *
 * Rotation copies the authenticated values to a new EncryptedSharedPreferences
 * file protected by a new AES-256-GCM MasterKey, then removes the old file.
 */
object EncryptedPreferencesStore {
    private const val METADATA_FILE = "notimind_encrypted_preferences_metadata"
    private const val VERSION_PREFIX = "version_"
    private const val INITIAL_VERSION = 1

    fun open(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext
        val version = metadata(appContext).getInt(versionKey(name), INITIAL_VERSION)
        return create(appContext, fileName(name, version), keyAlias(name, version))
    }

    @Synchronized
    fun rotate(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext
        val metadata = metadata(appContext)
        val currentVersion = metadata.getInt(versionKey(name), INITIAL_VERSION)
        val current = create(appContext, fileName(name, currentVersion), keyAlias(name, currentVersion))
        val nextVersion = currentVersion + 1
        val replacement = create(appContext, fileName(name, nextVersion), keyAlias(name, nextVersion))
        val editor = replacement.edit().clear()

        current.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Unable to commit encrypted preference key rotation" }
        check(metadata.edit().putInt(versionKey(name), nextVersion).commit()) {
            "Unable to commit encrypted preference key version"
        }
        check(current.edit().clear().commit()) { "Unable to clear rotated encrypted preferences" }
        appContext.deleteSharedPreferences(fileName(name, currentVersion))
        return replacement
    }

    private fun create(context: Context, fileName: String, alias: String): SharedPreferences {
        val masterKey =
            MasterKey
                .Builder(context, alias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun metadata(context: Context): SharedPreferences =
        context.getSharedPreferences(METADATA_FILE, Context.MODE_PRIVATE)

    private fun versionKey(name: String): String = VERSION_PREFIX + name

    private fun fileName(name: String, version: Int): String = "$name.v$version"

    private fun keyAlias(name: String, version: Int): String = "notimind_${name}_master_v$version"
}
