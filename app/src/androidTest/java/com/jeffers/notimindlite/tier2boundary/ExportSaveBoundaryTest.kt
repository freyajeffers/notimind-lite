package com.jeffers.notimindlite.tier2boundary

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.util.DatabaseExporter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Boundary test for saving an already-created export to a caller-chosen destination.
 *
 * The Save action in the export dialog copies bytes rather than regenerating them, so the
 * copy must be byte-identical and must report failure instead of throwing when the
 * destination cannot be opened.
 */
@RunWith(AndroidJUnit4::class)
class ExportSaveBoundaryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun saveExportTo_copiesFileByteForByte() {
        val payload = ByteArray(4096) { index -> (index % 251).toByte() }
        val source = File(context.cacheDir, "export-save-source.bin").apply { writeBytes(payload) }
        val destination = File(context.cacheDir, "export-save-dest.bin").apply { delete() }
        try {
            val saved = DatabaseExporter.saveExportTo(context, source, Uri.fromFile(destination))

            assertTrue("save should report success", saved)
            assertArrayEquals(payload, destination.readBytes())
        } finally {
            source.delete()
            destination.delete()
        }
    }

    @Test
    fun saveExportTo_reportsFailureWhenDestinationHasNoParent() {
        val source = File(context.cacheDir, "export-save-source-2.bin").apply { writeText("payload") }
        val unwritable = File("/proc/definitely-not-writable/out.bin")
        try {
            val saved = DatabaseExporter.saveExportTo(context, source, Uri.fromFile(unwritable))

            assertFalse("save must fail rather than throw", saved)
        } finally {
            source.delete()
        }
    }
}
