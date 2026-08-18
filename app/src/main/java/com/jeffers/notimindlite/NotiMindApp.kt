package com.jeffers.notimindlite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.multidex.MultiDexApplication

/**
 * NotiMindApp is the main application class.
 * It implements ComponentCallbacks2 to manage memory trimming and 
 * ensures the application responds to OS memory pressure events.
 */
class NotiMindApp : MultiDexApplication(), android.content.ComponentCallbacks2 {
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
                // UI is no longer visible; we can release some UI-specific resources
                com.jeffers.notimindlite.util.AppIconCache.clearCache()
                Log.d(TAG, "UI Hidden: Cleared AppIconCache")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // App is running but memory is critical; clear all caches immediately
                com.jeffers.notimindlite.util.AppIconCache.clearCache()
                Log.w(TAG, "Running Low/Critical: Forced cache clear")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // OS is about to kill the process; clear everything
                com.jeffers.notimindlite.util.AppIconCache.clearCache()
                Log.w(TAG, "Trim Memory Complete: Final cache clear")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle global configuration changes if necessary
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory triggered: Clearing all caches")
        com.jeffers.notimindlite.util.AppIconCache.clearCache()
    }
}
