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

    @Test
    fun floatArrayConversion_roundTripsSpecialFloatsAndInfinities() {
        val original = floatArrayOf(
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            -0.0f,
            0.0f,
            Float.MIN_VALUE,
            Float.MAX_VALUE
        )
        val bytes = converters.fromFloatArray(original)
        assertNotNull(bytes)
        val restored = converters.toFloatArray(bytes)
        assertNotNull(restored)
        assertEquals(original.size, restored!!.size)
        assertEquals(Float.POSITIVE_INFINITY, restored[0], 0.0f)
        assertEquals(Float.NEGATIVE_INFINITY, restored[1], 0.0f)
        assertEquals(-0.0f, restored[2], 0.0f)
        assertEquals(0.0f, restored[3], 0.0f)
        assertEquals(Float.MIN_VALUE, restored[4], 0.0f)
        assertEquals(Float.MAX_VALUE, restored[5], 0.0f)
    }

    @Test
    fun floatArrayConversion_handlesUnalignedByteArrayGracefully() {
        // 7 bytes: should extract 7 / 4 = 1 float and ignore trailing 3 unaligned bytes
        val unalignedBytes = byteArrayOf(0, 0, 0, 0, 1, 2, 3)
        val restored = converters.toFloatArray(unalignedBytes)
        assertNotNull(restored)
        assertEquals(1, restored!!.size)
        assertEquals(0.0f, restored[0], 0.0f)
    }

    @Test
    fun floatArrayConversion_roundTripsLargeEmbeddingVector() {
        val size = 768
        val vector = FloatArray(size) { i -> (i.toFloat() * 0.001f) - 0.384f }
        val bytes = converters.fromFloatArray(vector)
        assertNotNull(bytes)
        assertEquals(size * 4, bytes!!.size)

        val restored = converters.toFloatArray(bytes)
        assertNotNull(restored)
        assertArrayEquals(vector, restored!!, 0.000001f)
    }
}
