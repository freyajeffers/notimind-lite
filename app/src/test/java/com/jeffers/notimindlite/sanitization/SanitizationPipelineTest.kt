package com.jeffers.notimindlite.sanitization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizationPipelineTest {
    @Test
    fun `drop when package blacklisted`() {
        val mgr = PackageFilterManager(blacklist = setOf("com.example.bad"))
        val pipeline = SanitizationPipeline(packageFilterManager = mgr)
        val out = pipeline.sanitize("com.example.bad", "hi", "there", null, null)
        assertNull(out)
    }

    @Test
    fun `redact title and content`() {
        val pipeline = SanitizationPipeline()
        val out = pipeline.sanitize("com.example.app", "Call +1 (555) 123-4567", "alice@example.com", null, null)
        assertNotNull(out)
        assertTrue(out!!.title.contains("[REDACTED-PHONE]") || out.content.contains("[REDACTED-EMAIL]"))
    }

    @Test
    fun `redact subText and bigText when present`() {
        val pipeline = SanitizationPipeline()
        val out = pipeline.sanitize(
            "com.example.app",
            "Clean Title",
            "Clean Content",
            "Contact bob@company.org",
            "Call 555-555-5555 for support"
        )
        assertNotNull(out)
        assertEquals("Clean Title", out!!.title)
        assertEquals("Clean Content", out.content)
        assertTrue(out.subText?.contains("[REDACTED-EMAIL]") == true)
        assertTrue(out.bigText?.contains("[REDACTED-PHONE]") == true)
    }

    @Test
    fun `null fields are safely handled without crashing`() {
        val pipeline = SanitizationPipeline()
        val out = pipeline.sanitize("com.example.app", null, null, null, null)
        assertNotNull(out)
        assertEquals("", out!!.title)
        assertEquals("", out.content)
        assertNull(out.subText)
        assertNull(out.bigText)
    }

    @Test
    fun `clean notification is preserved intact`() {
        val pipeline = SanitizationPipeline()
        val out = pipeline.sanitize(
            "com.example.app",
            "System Update",
            "Version 2.0 is available for installation.",
            "Settings",
            "Tap to download"
        )
        assertNotNull(out)
        assertEquals("System Update", out!!.title)
        assertEquals("Version 2.0 is available for installation.", out.content)
        assertEquals("Settings", out.subText)
        assertEquals("Tap to download", out.bigText)
    }
}
