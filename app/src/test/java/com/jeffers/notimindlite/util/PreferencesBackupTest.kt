package com.jeffers.notimindlite.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferencesBackupTest {
    private lateinit var context: Context
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempFile = File.createTempFile("prefs_backup_test", ".json")
        context
            .getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
        context
            .getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun exportPreferences_writesAllSupportedTypesToJsonFile() {
        val prefs = context.getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putBoolean("test_bool", true)
            .putInt("test_int", 42)
            .putLong("test_long", 9876543210L)
            .putFloat("test_float", 3.14f)
            .putString("test_string", "sample value")
            .commit()

        val exportedFile = PreferencesBackup.exportPreferences(context, tempFile.absolutePath)
        assertTrue(exportedFile.exists())
        val jsonText = exportedFile.readText()
        assertTrue(jsonText.contains("\"test_bool\": true"))
        assertTrue(jsonText.contains("\"test_int\": 42"))
        assertTrue(jsonText.contains("\"test_long\": 9876543210"))
        assertTrue(jsonText.contains("\"test_string\": \"sample value\""))
    }

    @Test
    fun importPreferences_restoresAllExportedTypesIntoSharedPreferences() {
        val prefs = context.getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putBoolean("key_bool", false)
            .putInt("key_int", 100)
            .putLong("key_long", 9876543210L)
            .putFloat("key_float", 2.5f)
            .putString("key_string", "restored text")
            .commit()

        PreferencesBackup.exportPreferences(context, tempFile.absolutePath)

        // Clear shared preferences
        prefs.edit().clear().commit()
        assertFalse(prefs.contains("key_bool"))

        // Import
        PreferencesBackup.importPreferences(context, tempFile.absolutePath)

        assertEquals(false, prefs.getBoolean("key_bool", true))
        assertEquals(100, prefs.getInt("key_int", 0))
        assertEquals(9876543210L, prefs.getLong("key_long", 0L))
        assertEquals(2.5f, prefs.getFloat("key_float", 0f), 0.001f)
        assertEquals("restored text", prefs.getString("key_string", null))
    }

    @Test
    fun importPreferences_missingFile_throwsIllegalArgumentException() {
        val missingPath = "/path/to/nonexistent/prefs_backup_${System.currentTimeMillis()}.json"
        assertThrows(IllegalArgumentException::class.java) {
            PreferencesBackup.importPreferences(context, missingPath)
        }
    }
}
