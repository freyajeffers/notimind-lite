package com.jeffers.notimindlite.tier1feature

import com.jeffers.notimindlite.migration.SecureFileShredder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SecureFileShredderTest {
    @Test
    fun shredOverwritesAndDeletesRegularFile() {
        val file = Files.createTempFile("notimind-shred", ".db").toFile()
        file.writeBytes(ByteArray(4097) { 0x41 })

        assertTrue(SecureFileShredder.shred(file, chunkSize = 257))
        assertFalse(file.exists())
    }

    @Test
    fun shredRefusesSymbolicLink() {
        val target = Files.createTempFile("notimind-shred-target", ".db").toFile()
        val link = target.resolveSibling("${target.name}.link").toPath()
        Files.createSymbolicLink(link, target.toPath())

        try {
            assertFalse(SecureFileShredder.shred(link.toFile()))
            assertTrue(target.exists())
        } finally {
            Files.deleteIfExists(link)
            target.delete()
        }
    }

    @Test
    fun shredTreatsMissingFileAsAlreadyRemoved() {
        val file = Files.createTempDirectory("notimind-shred").resolve("missing.db").toFile()

        assertTrue(SecureFileShredder.shred(file))
    }

    @Test
    fun shredRefusesDirectory() {
        val dir = Files.createTempDirectory("notimind-shred-dir").toFile()
        try {
            assertFalse(SecureFileShredder.shred(dir))
            assertTrue(dir.exists())
        } finally {
            dir.delete()
        }
    }

    @Test
    fun shredHandlesEmptyZeroByteFileSuccessfully() {
        val file = Files.createTempFile("notimind-empty", ".db").toFile()
        try {
            assertTrue(SecureFileShredder.shred(file))
            assertFalse(file.exists())
        } finally {
            if (file.exists()) file.delete()
        }
    }

    @Test
    fun shredRequiresPositiveChunkSize() {
        val file = Files.createTempFile("notimind-chunk", ".db").toFile()
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                SecureFileShredder.shred(file, chunkSize = 0)
            }
        } finally {
            file.delete()
        }
    }
}
