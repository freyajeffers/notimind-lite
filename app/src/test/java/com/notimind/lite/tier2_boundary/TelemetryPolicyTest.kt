package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.util.TelemetryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryPolicyTest {
    @Test
    fun `telemetry levels are parsed and ordered`() {
        assertEquals(TelemetryManager.Level.MINIMAL, TelemetryManager.parseLevel("minimal"))
        assertEquals(TelemetryManager.Level.STANDARD, TelemetryManager.parseLevel("standard"))
        assertEquals(TelemetryManager.Level.FULL, TelemetryManager.parseLevel("full"))
        assertEquals(TelemetryManager.Level.MINIMAL, TelemetryManager.parseLevel("unknown"))
    }

    @Test
    fun `disabled telemetry never allows events`() {
        TelemetryManager.configure(androidx.test.core.app.ApplicationProvider.getApplicationContext(), false, "full")
        assertFalse(TelemetryManager.isEnabled())
        assertFalse(TelemetryManager.allows(TelemetryManager.Level.MINIMAL))
        TelemetryManager.configure(androidx.test.core.app.ApplicationProvider.getApplicationContext(), true, "standard")
        assertTrue(TelemetryManager.allows(TelemetryManager.Level.MINIMAL))
        assertFalse(TelemetryManager.allows(TelemetryManager.Level.FULL))
    }
}
