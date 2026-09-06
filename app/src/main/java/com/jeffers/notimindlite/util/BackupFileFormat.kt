package com.jeffers.notimindlite.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Binary header for encrypted backup files.
 *
 * Layout:
 * [4B magic "NMB1"][1B version][1B keySource]
 *   [if keySource == PASSPHRASE_WRAPPED: 16B salt, 4B iterations, 12B kekIv, 4B len, NB wrappedDek]
 * [12B dataIv][MB ciphertext + 16B GCM tag]
 */
internal object BackupFileFormat {
    /** ASCII "NMB1" — NotiMind Backup v1. */
    val MAGIC: ByteArray = "NMB1".toByteArray(Charsets.US_ASCII)
    private const val MAGIC_LEN = 4
    const val CURRENT_VERSION: Byte = 0x01

    /** DEK from KeyStore only. Restorable on same device only. */
    const val KEY_SOURCE_KEYSTORE_ONLY: Byte = 0x01

    /** DEK wrapped under passphrase KEK. Restorable cross-device. */
    const val KEY_SOURCE_PASSPHRASE_WRAPPED: Byte = 0x02

    const val IV_LEN: Int = 12
    const val PBKDF2_SALT_LEN: Int = 16
    const val PBKDF2_ITERATIONS: Int = 600_000

    private const val MIN_PLAUSIBLE_ITERATIONS = 1_000
    private const val MAX_PLAUSIBLE_ITERATIONS = 10_000_000
    private const val MAX_WRAPPED_DEK_LEN = 256
    private const val GCM_AUTH_TAG_LEN = 16
    private const val HEADER_BASE_OVERHEAD = 6
    private const val INT_SIZE_BYTES = 4

    data class WrappedPayload(
        val salt: ByteArray,
        val iterations: Int,
        val kekIv: ByteArray,
        val wrappedDek: ByteArray,
        val dataIv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    data class ParsedHeader(
        val version: Byte,
        val keySource: Byte,
        val pbkdf2Iterations: Int,
        val pbkdf2Salt: ByteArray,
        val kekIv: ByteArray,
        val wrappedDek: ByteArray,
        val dataIv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    class FormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    private fun fail(message: String): Nothing = throw FormatException(message)

    fun writeWrapped(payload: WrappedPayload): ByteArray {
        val out = ByteArrayOutputStream(
            HEADER_BASE_OVERHEAD + PBKDF2_SALT_LEN + INT_SIZE_BYTES +
                IV_LEN + INT_SIZE_BYTES + payload.wrappedDek.size +
                IV_LEN + payload.ciphertext.size
        )
        val dos = DataOutputStream(out)
        dos.write(MAGIC)
        dos.writeByte(CURRENT_VERSION.toInt())
        dos.writeByte(KEY_SOURCE_PASSPHRASE_WRAPPED.toInt())
        dos.write(payload.salt)
        dos.writeInt(payload.iterations)
        dos.write(payload.kekIv)
        dos.writeInt(payload.wrappedDek.size)
        dos.write(payload.wrappedDek)
        dos.write(payload.dataIv)
        dos.write(payload.ciphertext)
        dos.flush()
        return out.toByteArray()
    }

    private class WrappedDekMetadata(
        val salt: ByteArray,
        val iterations: Int,
        val kekIv: ByteArray,
        val wrappedDek: ByteArray,
    )

    private fun readWrappedMetadata(dis: DataInputStream): WrappedDekMetadata {
        val salt = ByteArray(PBKDF2_SALT_LEN).also { dis.readFully(it) }
        val iterations = dis.readInt()
        if (iterations < MIN_PLAUSIBLE_ITERATIONS || iterations > MAX_PLAUSIBLE_ITERATIONS) {
            fail("Implausible iteration count: $iterations")
        }
        val kekIv = ByteArray(IV_LEN).also { dis.readFully(it) }
        val len = dis.readInt()
        if (len <= 0 || len > MAX_WRAPPED_DEK_LEN) fail("Implausible DEK length: $len")
        val wrappedDek = ByteArray(len).also { dis.readFully(it) }
        return WrappedDekMetadata(salt, iterations, kekIv, wrappedDek)
    }

    @Suppress("ThrowsCount") // Multiple integrity validations fail-fast with specific reasons
    fun parse(bytes: ByteArray): ParsedHeader {
        if (bytes.size < HEADER_BASE_OVERHEAD + IV_LEN) {
            fail("File too short to contain a valid header")
        }

        val dis = DataInputStream(ByteArrayInputStream(bytes))
        try {
            val magic = ByteArray(MAGIC_LEN)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) fail("Not a NotiMind backup file")
            val version = dis.readByte()
            if (version > CURRENT_VERSION) fail("Unsupported backup version: $version")
            val keySource = dis.readByte()

            val meta = when (keySource) {
                KEY_SOURCE_PASSPHRASE_WRAPPED -> readWrappedMetadata(dis)
                KEY_SOURCE_KEYSTORE_ONLY -> WrappedDekMetadata(
                    salt = ByteArray(0),
                    iterations = 0,
                    kekIv = ByteArray(0),
                    wrappedDek = ByteArray(0),
                )
                else -> fail("Unknown key source: $keySource")
            }

            val dataIv = ByteArray(IV_LEN).also { dis.readFully(it) }
            val ciphertext = dis.readBytes()
            if (ciphertext.size <= GCM_AUTH_TAG_LEN) fail("Ciphertext payload missing or truncated")

            return ParsedHeader(
                version = version,
                keySource = keySource,
                pbkdf2Iterations = meta.iterations,
                pbkdf2Salt = meta.salt,
                kekIv = meta.kekIv,
                wrappedDek = meta.wrappedDek,
                dataIv = dataIv,
                ciphertext = ciphertext,
            )
        } catch (e: FormatException) {
            throw e
        } catch (e: IOException) {
            throw FormatException("Malformed backup header: ${e.message}", e)
        }
    }
}
