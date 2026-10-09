package com.jeffers.notimindlite.migration

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Best-effort destruction of a regular plaintext file before unlinking it.
 *
 * Secure erasure cannot be guaranteed on journaling filesystems or flash storage. This helper
 * still overwrites every currently allocated byte, forces the file channel, and refuses symbolic
 * links so migration code does not accidentally modify an unexpected target.
 */
object SecureFileShredder {
    private const val DEFAULT_CHUNK_SIZE = 1024 * 1024

    @Suppress("ReturnCount")
    fun shred(file: File, chunkSize: Int = DEFAULT_CHUNK_SIZE): Boolean {
        require(chunkSize > 0) { "chunkSize must be positive" }
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false

        return try {
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                val length = randomAccessFile.length()
                val zeros = ByteArray(chunkSize)
                var remaining = length
                while (remaining > 0) {
                    val count = minOf(remaining, zeros.size.toLong()).toInt()
                    randomAccessFile.write(zeros, 0, count)
                    remaining -= count
                }
                randomAccessFile.fd.sync()
            }
            Files.deleteIfExists(path)
        } catch (_: Exception) {
            false
        }
    }
}
