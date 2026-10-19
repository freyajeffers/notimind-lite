package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseExportHelperTest {
    @Test
    fun sanitizeCsvField_nullInput_returnsEmptyQuotes() {
        assertEquals("\"\"", DatabaseExportHelper.sanitizeCsvField(null))
    }

    @Test
    fun sanitizeCsvField_normalText_wrapsInQuotes() {
        assertEquals("\"Simple text\"", DatabaseExportHelper.sanitizeCsvField("Simple text"))
    }

    @Test
    fun sanitizeCsvField_containsQuotes_escapesQuotesDouble() {
        assertEquals(
            "\"He said \"\"Hello\"\" to everyone\"",
            DatabaseExportHelper.sanitizeCsvField("He said \"Hello\" to everyone")
        )
    }

    @Test
    fun sanitizeCsvField_formulaInjectionPrefixes_prependsSingleQuote() {
        val formulas = listOf(
            "=SUM(A1:A10)",
            "+1+2",
            "-5+10",
            "@SUM",
            "\tTAB_COMMAND",
            "\rRET_COMMAND"
        )
        for (formula in formulas) {
            val sanitized = DatabaseExportHelper.sanitizeCsvField(formula)
            assertTrue(
                "Expected formula '$formula' to be escaped with single quote, got '$sanitized'",
                sanitized.startsWith("\"'")
            )
        }
    }

    @Test
    fun sanitizeCsvField_leadingSpacesBeforeFormula_escapesFormula() {
        val formulaWithLeadingSpaces = "   =1+1"
        val sanitized = DatabaseExportHelper.sanitizeCsvField(formulaWithLeadingSpaces)
        assertTrue(
            "Expected leading space formula to be escaped with single quote",
            sanitized.startsWith("\"'")
        )
    }

    @Test
    fun exportToNdjsonString_emptyList_returnsEmptyString() {
        val result = DatabaseExportHelper.exportToNdjsonString(emptyList())
        assertEquals("", result)
    }

    @Test
    fun exportToNdjsonString_singleNotification_formatsValidJsonLine() {
        val entity = NotificationEntity(
            id = 42,
            key = "com.test.pkg_42",
            packageName = "com.test.pkg",
            appName = "TestApp",
            title = "Test Title",
            content = "Test Content",
            postTime = 1700000000000L,
            isDismissed = false,
            isPinned = true
        )

        val ndjson = DatabaseExportHelper.exportToNdjsonString(listOf(entity))
        val lines = ndjson.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)

        val line = lines[0]
        assertTrue(line.contains("\"id\":42"))
        assertTrue(line.contains("\"key\":\"com.test.pkg_42\""))
        assertTrue(line.contains("\"packageName\":\"com.test.pkg\""))
        assertTrue(line.contains("\"appName\":\"TestApp\""))
        assertTrue(line.contains("\"title\":\"Test Title\""))
        assertTrue(line.contains("\"content\":\"Test Content\""))
        assertTrue(line.contains("\"postTime\":1700000000000"))
        assertTrue(line.contains("\"isDismissed\":false"))
        assertTrue(line.contains("\"isPinned\":true"))
    }

    @Test
    fun exportToCsvString_validNotifications_containsHeaderAndRows() {
        val entity = NotificationEntity(
            id = 99,
            key = "com.test.app_99",
            packageName = "com.test.app",
            appName = "AppTitle",
            title = "=MaliciousFormula",
            content = "Normal Content",
            postTime = 1600000000000L,
            isDismissed = true,
            dismissReason = 1,
            isPinned = false
        )

        val csv = DatabaseExportHelper.exportToCsvString(listOf(entity))
        val lines = csv.lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 2)
        assertEquals("ID,Package,AppName,Title,Content,PostTime,IsDismissed,DismissReason,IsPinned", lines[0])

        val row = lines[1]
        assertTrue(row.contains("com.test.app"))
        assertTrue(row.contains("\"'=MaliciousFormula\""))
        assertTrue(row.contains("true"))
    }
}
