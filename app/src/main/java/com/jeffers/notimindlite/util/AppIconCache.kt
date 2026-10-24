package com.jeffers.notimindlite.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * AppIconCache provides memory-efficient caching of decoded app icons.
 * It uses an LruCache to prevent redundant decoding of icons during fast scrolling
 * in notification lists, significantly reducing UI stutter.
 */
object AppIconCache {
    private const val TAG = "AppIconCache"
    private const val KIBIBYTE = 1024
    private const val CACHE_MEMORY_FRACTION = 8
    private const val DEFAULT_TARGET_SIZE = 64

    private val cacheSize = (Runtime.getRuntime().maxMemory() / KIBIBYTE).toInt() /
        CACHE_MEMORY_FRACTION

    private val iconCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / KIBIBYTE
    }

    fun getIcon(context: Context, uri: String?, targetSize: Int = DEFAULT_TARGET_SIZE): Bitmap? {
        val cached = uri?.let { iconCache.get(it) }
        return if (uri == null || cached != null) {
            cached
        } else {
            try {
                val bitmap = decodeSampledBitmapFromUri(context, uri, targetSize)
                if (bitmap != null) {
                    iconCache.put(uri, bitmap)
                }
                bitmap
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "Icon not found for uri=$uri", e)
                null
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception reading icon for uri=$uri", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "I/O error decoding icon for uri=$uri", e)
                null
            }
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uriString: String, targetSize: Int): Bitmap? = try {
        val uri = uriString.toUri()
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // First pass: get dimensions
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }

        // Calculate sample size and decode actual bitmap
        options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
        options.inJustDecodeBounds = false

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
    } catch (e: FileNotFoundException) {
        Log.w(TAG, "Icon file not found: $uriString", e)
        null
    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception opening icon stream: $uriString", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Malformed uri for icon: $uriString", e)
        null
    } catch (e: IOException) {
        Log.e(TAG, "I/O error decoding icon: $uriString", e)
        null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun clearCache() {
        iconCache.evictAll()
    }
}
