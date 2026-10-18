package com.jeffers.notimindlite.data.local

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    private companion object {
        const val BYTES_PER_FLOAT = 4
    }

    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val buffer = ByteBuffer.allocate(value.size * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(value.size / BYTES_PER_FLOAT)
        for (i in floatArray.indices) {
            floatArray[i] = buffer.float
        }
        return floatArray
    }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? = if (value != null) SyncStatus.valueOf(value) else null
}
