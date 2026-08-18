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

/**
 * NotiMindApp is the main application class.
 * It implements ComponentCallbacks2 to manage memory trimming and 
 * ensures the application responds to OS memory pressure events.
 */
class NotiMindApp : Application(), android.content.ComponentCallbacks2 {
    companion object {
        private const val TAG = "NotiMindApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NotiMind Lite Application Initialized")
    }

    /**
     * Triggered by the OS when the system is running low on memory.
     * We use this to clear non-essential caches to prevent OOM crashes.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory triggered with level: $level")
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                AppIconCache.clearCache()
                Log.d(TAG, "UI Hidden: Cleared AppIconCache")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                AppIconCache.clearCache()
                Log.w(TAG, "Running Low/Critical: Forced cache clear")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                AppIconCache.clearCache()
                Log.w(TAG, "Trim Memory Complete: Final cache clear")
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
