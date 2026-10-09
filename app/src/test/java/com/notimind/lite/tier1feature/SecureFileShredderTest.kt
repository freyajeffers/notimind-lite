package com.notimind.lite.tier1feature

import com.jeffers.notimindlite.migration.SecureFileShredder
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
