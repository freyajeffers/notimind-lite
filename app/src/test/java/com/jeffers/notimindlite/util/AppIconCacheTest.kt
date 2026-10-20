package com.jeffers.notimindlite.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIconCacheTest {
    private lateinit var context: Context
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AppIconCache.clearCache()
    }

    @After
    fun tearDown() {
        AppIconCache.clearCache()
        createdFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @Test
    fun getIcon_nullUri_returnsNull() {
        val result = AppIconCache.getIcon(context, null)
        assertNull(result)
    }

    @Test
    fun getIcon_nonExistentFileUri_returnsNull() {
        val result = AppIconCache.getIcon(context, "file:///nonexistent/path/to/icon.png")
        assertNull(result)
    }

    @Test
    fun getIcon_validFileUri_decodesAndCachesBitmap() {
        val file = createPngFile("test_icon.png", width = 32, height = 32)
        val uri = Uri.fromFile(file).toString()

        val decoded1 = AppIconCache.getIcon(context, uri, targetSize = 32)
        assertNotNull(decoded1)

        // Second call should retrieve from cache
        val decoded2 = AppIconCache.getIcon(context, uri, targetSize = 32)
        assertNotNull(decoded2)
        assertEquals(decoded1, decoded2)
    }

    @Test
    fun getIcon_largeImage_downsamplesAccordingToTargetSize() {
        val file = createPngFile("large_icon.png", width = 256, height = 256)
        val uri = Uri.fromFile(file).toString()

        val decoded = AppIconCache.getIcon(context, uri, targetSize = 64)
        assertNotNull(decoded)
        // With calculateInSampleSize, 256x256 targeted for 64x64 should sample down
        assertTrue(decoded!!.width <= 128)
        assertTrue(decoded.height <= 128)
    }

    @Test
    fun clearCache_evictsCachedEntries() {
        val file = createPngFile("evict_icon.png", width = 32, height = 32)
        val uri = Uri.fromFile(file).toString()

        val decoded1 = AppIconCache.getIcon(context, uri, targetSize = 32)
        assertNotNull(decoded1)

        AppIconCache.clearCache()

        // Delete the backing file so a cache miss would return null
        file.delete()

        val decoded2 = AppIconCache.getIcon(context, uri, targetSize = 32)
        assertNull(decoded2)
    }

    private fun createPngFile(fileName: String, width: Int, height: Int): File {
        val file = File(context.cacheDir, fileName)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        createdFiles.add(file)
        return file
    }
}
