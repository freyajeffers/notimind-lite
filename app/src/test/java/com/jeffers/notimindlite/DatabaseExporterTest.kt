package com.jeffers.notimindlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.util.DatabaseExporter
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseExporterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testJsonExportSchemaValidityAndEscaping() {
        val entity = NotificationEntity(
            id = 101,
            key = "com.test.app_101",
            packageName = "com.test.app",
            appName = "Test App",
            title = "Title with \"quotes\" and \n newlines",
            content = "Content with \\ slashes and \t tabs",
            postTime = 1600000000000L,
            isDismissed = false,
            isPinned = true,
            isPersistent = false
        )

        val jsonString = DatabaseExporter.exportToJsonString(listOf(entity))
        assertNotNull(jsonString)

        val jsonArray = JSONArray(jsonString)
        assertEquals(1, jsonArray.length())

        val obj = jsonArray.getJSONObject(0)
        assertEquals(101L, obj.getLong("id"))
        assertEquals("com.test.app_101", obj.getString("key"))
        assertEquals("com.test.app", obj.getString("packageName"))
        assertEquals("Test App", obj.getString("appName"))
        assertEquals("Title with \"quotes\" and \n newlines", obj.getString("title"))
        assertEquals("Content with \\ slashes and \t tabs", obj.getString("content"))
        assertEquals(1600000000000L, obj.getLong("postTime"))
        assertFalse(obj.getBoolean("isDismissed"))
        assertTrue(obj.getBoolean("isPinned"))
    }

    @Test
    fun testCsvFormulaInjectionSanitization() {
        val entityEqual = NotificationEntity(key = "k1", packageName = "p", appName = "App", title = "=1+1", content = "=SUM(A1:A10)")
        val entityPlus = NotificationEntity(key = "k2", packageName = "p", appName = "App", title = "+1-2", content = "+1+1")
        val entityMinus = NotificationEntity(key = "k3", packageName = "p", appName = "App", title = "-1+2", content = "-cmd|' /C calc'!A0")
        val entityAt = NotificationEntity(key = "k4", packageName = "p", appName = "App", title = "@SUM(1,2)", content = "@appName")
        val entityTab = NotificationEntity(key = "k5", packageName = "p", appName = "App", title = "\tTabbed", content = "\rCarriage")
        val entityLeadingSpaceFormula = NotificationEntity(key = "k6", packageName = "p", appName = "App", title = "   =1+1", content = "  +2")

        val csvString = DatabaseExporter.exportToCsvString(
            listOf(entityEqual, entityPlus, entityMinus, entityAt, entityTab, entityLeadingSpaceFormula)
        )
        
        assertNotNull(csvString)

        assertTrue(csvString.contains("\"'=1+1\""))
        assertTrue(csvString.contains("\"'=SUM(A1:A10)\""))
        assertTrue(csvString.contains("\"'+1-2\""))
        assertTrue(csvString.contains("\"'-1+2\""))
        assertTrue(csvString.contains("\"'@SUM(1,2)\""))
        assertTrue(csvString.contains("\"'\tTabbed\""))
        assertTrue(csvString.contains("\"'   =1+1\""))
    }

    @Test
    fun testFileProviderUriGeneration() {
        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) exportsDir.mkdirs()

        val dummyFile = File(exportsDir, "test_export.csv")
        dummyFile.writeText("test content")

        val uri = DatabaseExporter.getExportFileUri(context, dummyFile)
        assertNotNull(uri)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue(uri.path?.contains("exports/test_export.csv") == true)
    }

    @Test
    fun testAutomaticCleanupRoutine() {
        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) exportsDir.mkdirs()

        val oldFile = File(exportsDir, "old_export.json")
        oldFile.writeText("{}")
        oldFile.setLastModified(System.currentTimeMillis() - 7200_000L) // 2 hours old

        val recentFile = File(exportsDir, "recent_export.json")
        recentFile.writeText("{}")
        recentFile.setLastModified(System.currentTimeMillis())

        DatabaseExporter.cleanupExportFiles(context, maxAgeMillis = 3600_000L) // 1 hour max age

        assertFalse("Old export file should be deleted", oldFile.exists())
        assertTrue("Recent export file should be retained", recentFile.exists())
    }
}
