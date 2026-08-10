package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.util.DatabaseExporter
import com.notimind.lite.base.BaseRobolectricTest
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExportSanitizationBoundaryTest : BaseRobolectricTest() {

    @Test
    fun tc_EXP_001_jsonExportValidSchemaAndEscaping() {
        val entity1 = createDummyEntity(
            key = "k_json_1",
            title = "Title with \"quotes\" and \n newlines",
            content = "Content with \\ slashes and \t tabs"
        )
        val entity2 = createDummyEntity(key = "k_json_2", title = "Simple", content = "Simple")

        val jsonStr = DatabaseExporter.exportToJsonString(listOf(entity1, entity2))
        assertNotNull(jsonStr)

        val jsonArray = JSONArray(jsonStr)
        assertEquals(2, jsonArray.length())

        val obj1 = jsonArray.getJSONObject(0)
        assertEquals("k_json_1", obj1.getString("key"))
        assertEquals("Title with \"quotes\" and \n newlines", obj1.getString("title"))
        assertEquals("Content with \\ slashes and \t tabs", obj1.getString("content"))
    }

    @Test
    fun tc_EXP_002_csvExportFormulaInjectionEscaping() {
        val formulaEqual = createDummyEntity(key = "k_eq", title = "=1+1", content = "=SUM(A1:A10)")
        val formulaPlus = createDummyEntity(key = "k_plus", title = "+1-2", content = "+1+1")
        val formulaMinus = createDummyEntity(key = "k_minus", title = "-1+2", content = "-cmd|' /C calc'!A0")
        val formulaAt = createDummyEntity(key = "k_at", title = "@SUM(1,2)", content = "@appName")
        val formulaTab = createDummyEntity(key = "k_tab", title = "\tTabbed", content = "Tabbed Content")

        val csvStr = DatabaseExporter.exportToCsvString(listOf(formulaEqual, formulaPlus, formulaMinus, formulaAt, formulaTab))
        assertNotNull(csvStr)

        val lines = csvStr.lines().filter { it.isNotBlank() }
        assertEquals(6, lines.size) // 1 header + 5 data rows

        // Verify formula characters are escaped with leading single quote inside quotes
        assertTrue("Formula equal must be escaped with single quote", csvStr.contains("\"'=1+1\""))
        assertTrue("Formula equal content must be escaped", csvStr.contains("\"'=SUM(A1:A10)\""))
        assertTrue("Formula plus must be escaped with single quote", csvStr.contains("\"'+1-2\""))
        assertTrue("Formula minus must be escaped with single quote", csvStr.contains("\"'-1+2\""))
        assertTrue("Formula at must be escaped with single quote", csvStr.contains("\"'@SUM(1,2)\""))
        assertTrue("Formula tab must be escaped with single quote", csvStr.contains("\"'\tTabbed\""))
    }

    @Test
    fun tc_EXP_003_shareExportFileAndCleanup() {
        val notifications = listOf(createDummyEntity(key = "share_k1"))

        DatabaseExporter.shareExportFile(context, notifications, isJson = true)
        DatabaseExporter.shareExportFile(context, notifications, isJson = false)

        val exportsDir = File(context.cacheDir, "exports")
        assertTrue("Exports directory should be created", exportsDir.exists())
        val files = exportsDir.listFiles()
        assertNotNull(files)
        assertTrue("At least 2 export files created in cache", files!!.size >= 2)

        // Verify files can be cleaned up
        for (file in files) {
            file.delete()
        }
        assertEquals("Cache directory should be clean", 0, exportsDir.listFiles()?.size ?: 0)
    }
}
