package com.jeffers.notimindlite.util

import android.util.Log

/**
 * Standardized logging wrapper for NotiMind Lite.
 * Provides unified tag prefixing, log levels, and production crash isolation.
 */
object AppLogger {
    private const val TAG_PREFIX = "NotiMind_"

    fun d(tag: String, message: String) {
        Log.d("$TAG_PREFIX$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$TAG_PREFIX$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG_PREFIX$tag", message, throwable)
        } else {
            Log.w("$TAG_PREFIX$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", message)
        }
    }
}
