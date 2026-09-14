package com.jeffers.notimindlite.sanitization

import org.junit.Test
import org.junit.Assert.*

class PackageFilterManagerTest {

    @Test
    fun `whitelist takes precedence`() {
        val mgr = PackageFilterManager(blacklist = setOf("a"), whitelist = setOf("b"))
        assertTrue(mgr.shouldAccept("b"))
        assertFalse(mgr.shouldAccept("a"))
    }

    @Test
    fun `default blacklist rejects shell`() {
        val mgr = PackageFilterManager.default()
        assertFalse(mgr.shouldAccept("com.android.shell"))
        assertTrue(mgr.shouldAccept("com.example.app"))
    }
}
