package com.jeffers.notimindlite.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun floatArrayConversion_roundTripsValuesCorrectly() {
        val original = floatArrayOf(0.123f, -4.56f, 0.0f, 789.012f, Float.MAX_VALUE, Float.MIN_VALUE)

        val byteArray = converters.fromFloatArray(original)
        assertNotNull(byteArray)
        assertEquals(original.size * 4, byteArray!!.size)

        val restored = converters.toFloatArray(byteArray)
        assertNotNull(restored)
        assertArrayEquals(original, restored!!, 0.000001f)
    }

    @Test
    fun floatArrayConversion_handlesEmptyArray() {
        val original = floatArrayOf()
        val byteArray = converters.fromFloatArray(original)
        assertNotNull(byteArray)
        assertEquals(0, byteArray!!.size)

        val restored = converters.toFloatArray(byteArray)
        assertNotNull(restored)
        assertEquals(0, restored!!.size)
    }

    @Test
    fun floatArrayConversion_handlesNulls() {
        assertNull(converters.fromFloatArray(null))
        assertNull(converters.toFloatArray(null))
    }

    @Test
    fun syncStatusConversion_roundTripsAllEnumValues() {
        SyncStatus.entries.forEach { status ->
            val name = converters.fromSyncStatus(status)
            assertEquals(status.name, name)

            val parsed = converters.toSyncStatus(name)
            assertEquals(status, parsed)
        }
    }

    @Test
    fun syncStatusConversion_handlesNull() {
        assertNull(converters.fromSyncStatus(null))
        assertNull(converters.toSyncStatus(null))
    }

    @Test
    fun syncStatusConversion_unknownStringThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toSyncStatus("UNKNOWN_SYNC_STATUS_VALUE")
        }
    }
}
