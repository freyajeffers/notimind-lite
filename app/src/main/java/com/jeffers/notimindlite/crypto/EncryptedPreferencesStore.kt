package com.jeffers.notimindlite.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Versioned AndroidKeyStore-backed preferences for secrets that must survive
 * process death but must not be stored as plaintext XML.
 *
 * Values are encrypted individually with AES-256-GCM. The preference file still
 * contains XML, but only encrypted values and type markers are persisted there.
 */
object EncryptedPreferencesStore {
    private const val METADATA_FILE = "notimind_encrypted_preferences_metadata"
    private const val VERSION_PREFIX = "version_"
    private const val INITIAL_VERSION = 1
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "notimind_encrypted_preferences_"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val TYPE_STRING = "s:"
    private const val TYPE_BOOLEAN = "b:"
    private const val TYPE_FLOAT = "f:"
    private const val TYPE_INT = "i:"
    private const val TYPE_LONG = "l:"
    private const val TYPE_STRING_SET = "t:"

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

    private fun create(context: Context, fileName: String, alias: String): SharedPreferences =
        KeystoreSharedPreferences(
            delegate = context.getSharedPreferences(fileName, Context.MODE_PRIVATE),
            key = key(alias)
        )

    private fun key(alias: String): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            val spec = KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            init(spec)
            generateKey()
        }
    }

    private fun metadata(context: Context): SharedPreferences =
        context.getSharedPreferences(METADATA_FILE, Context.MODE_PRIVATE)

    private fun versionKey(name: String): String = VERSION_PREFIX + name

    private fun fileName(name: String, version: Int): String = "$name.v$version"

    private fun keyAlias(name: String, version: Int): String = "$KEY_ALIAS_PREFIX${name}_v$version"

    private class KeystoreSharedPreferences(
        private val delegate: SharedPreferences,
        private val key: SecretKey
    ) : SharedPreferences {
        override fun getAll(): Map<String, *> = delegate
            .all
            .mapNotNull { (name, value) ->
                decode(value as? String)?.let { name to it }
            }.toMap()

        override fun getString(key: String?, defValue: String?): String? =
            decode(delegate.getString(key, null)) as? String ?: defValue

        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            when (val decoded = decode(delegate.getString(key, null))) {
                is Set<*> -> decoded.filterIsInstance<String>().toSet()
                else -> defValues
            }

        override fun getInt(key: String?, defValue: Int): Int =
            decode(delegate.getString(key, null)) as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            decode(delegate.getString(key, null)) as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            decode(delegate.getString(key, null)) as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            decode(delegate.getString(key, null)) as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = delegate.contains(key)

        override fun edit(): SharedPreferences.Editor = Editor(delegate.edit(), key)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = delegate.registerOnSharedPreferenceChangeListener(listener)

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = delegate.unregisterOnSharedPreferenceChangeListener(listener)

        private fun decode(value: String?): Any? {
            if (value == null) return null
            return runCatching {
                val bytes = Base64.decode(value, Base64.NO_WRAP)
                val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
                val ciphertext = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
                val plaintext = Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    doFinal(ciphertext).toString(StandardCharsets.UTF_8)
                }
                when {
                    plaintext.startsWith(TYPE_STRING) -> {
                        plaintext.removePrefix(TYPE_STRING)
                    }

                    plaintext.startsWith(TYPE_BOOLEAN) -> {
                        plaintext.removePrefix(TYPE_BOOLEAN).toBoolean()
                    }

                    plaintext.startsWith(TYPE_FLOAT) -> {
                        plaintext.removePrefix(TYPE_FLOAT).toFloat()
                    }

                    plaintext.startsWith(TYPE_INT) -> {
                        plaintext.removePrefix(TYPE_INT).toInt()
                    }

                    plaintext.startsWith(TYPE_LONG) -> {
                        plaintext.removePrefix(TYPE_LONG).toLong()
                    }

                    plaintext.startsWith(TYPE_STRING_SET) -> {
                        val values = JSONArray(plaintext.removePrefix(TYPE_STRING_SET))
                        buildSet { repeat(values.length()) { add(values.getString(it)) } }
                    }

                    else -> {
                        null
                    }
                }
            }.getOrNull()
        }
    }

    private class Editor(
        private val delegate: SharedPreferences.Editor,
        private val key: SecretKey
    ) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            putEncoded(key, value?.let { TYPE_STRING + it })

        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
            val encoded = values?.let {
                val json = JSONArray().apply { it.forEach(::put) }
                TYPE_STRING_SET + json
            }
            return putEncoded(key, encoded)
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            putEncoded(key, TYPE_INT + value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            putEncoded(key, TYPE_LONG + value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            putEncoded(key, TYPE_FLOAT + value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            putEncoded(key, TYPE_BOOLEAN + value)

        override fun remove(key: String?): SharedPreferences.Editor = delegate.remove(key)

        override fun clear(): SharedPreferences.Editor = delegate.clear()

        override fun commit(): Boolean = delegate.commit()

        override fun apply() = delegate.apply()

        private fun putEncoded(key: String?, value: String?): SharedPreferences.Editor {
            if (key == null) return delegate
            if (value == null) return delegate.remove(key)
            val iv = ByteArray(GCM_IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
            val ciphertext = Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.ENCRYPT_MODE, this@Editor.key, GCMParameterSpec(GCM_TAG_BITS, iv))
                doFinal(value.toByteArray(StandardCharsets.UTF_8))
            }
            return delegate.putString(key, Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP))
        }
    }
}
