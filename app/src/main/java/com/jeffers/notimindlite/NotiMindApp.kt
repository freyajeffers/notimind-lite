package com.jeffers.notimindlite

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import com.jeffers.notimindlite.util.AppIconCache

class NotiMindApp : Application(), android.content.ComponentCallbacks2 {
    companion object {
        private const val TAG = "NotiMindApp"
    }

    override fun onCreate() {
        super.onCreate()
        com.jeffers.notimindlite.util.AppInitializer.initialize(this)
        Log.i(TAG, "NotiMind Lite Application Initialized")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory triggered with level: $level")
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                AppIconCache.clearCache()
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                AppIconCache.clearCache()
                com.jeffers.notimindlite.util.VectorEmbeddingHelper.clearCache()
                System.gc()
                Log.w(TAG, "Running Low/Critical: Forced cache clear and GC")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                AppIconCache.clearCache()
                com.jeffers.notimindlite.util.VectorEmbeddingHelper.clearCache()
                System.gc()
                Log.w(TAG, "Trim Memory Complete: Final cache clear and GC")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory triggered: Clearing all caches")
        AppIconCache.clearCache()
    }
}
