package com.jeffers.notimindlite.sanitization

import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

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
}
