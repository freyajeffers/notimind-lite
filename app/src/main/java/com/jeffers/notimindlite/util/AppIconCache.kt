package com.jeffers.notimindlite.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import java.io.InputStream

/**
 * AppIconCache provides memory-efficient caching of decoded app icons.
 * It uses an LruCache to prevent redundant decoding of icons during fast scrolling
 * in notification lists, significantly reducing UI stutter.
 */
object AppIconCache {
    private const val TAG = "AppIconCache"
    
    private val cacheSize = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
    
    private val iconCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun getIcon(context: Context, uri: String?, targetSize: Int = 64): Bitmap? {
        if (uri == null) return null
        iconCache.get(uri)?.let { return it }
        
        return try {
            val bitmap = decodeSampledBitmapFromUri(context, uri, targetSize)
            if (bitmap != null) {
                iconCache.put(uri, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon for $uri", e)
            null
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uriString: String, targetSize: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                val resizedInputStream = context.contentResolver.openInputStream(uri)
                resizedInputStream?.use { finalStream ->
                    options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeStream(finalStream, null, options)
                }
            }
        } catch (e: Exception) {
            null
        }
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
        Log.d(TAG, "AppIconCache cleared")
    }
}
