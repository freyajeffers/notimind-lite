package com.jeffers.notimindlite

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.AppIconCache
import com.jeffers.notimindlite.util.DatabaseLockManager
import com.jeffers.notimindlite.util.VectorEmbeddingHelper

class NotiMindApp :
    Application(),
    android.content.ComponentCallbacks2 {
    companion object {
        private const val TAG = "NotiMindApp"
        private const val TRIM_MEMORY_RUNNING_LOW_LEVEL = 10
        private const val TRIM_MEMORY_RUNNING_CRITICAL_LEVEL = 15
        private const val TRIM_MEMORY_COMPLETE_LEVEL = 80
    }

    override fun onCreate() {
        super.onCreate()
        com.jeffers.notimindlite.util.AppInitializer
            .initialize(this)
        runCatching {
            val prefs = PreferencesRepository(this)
            if (prefs.securityManager.autoLockDb.value || prefs.securityManager.appLockEnabled.value) {
                DatabaseLockManager.unlock()
            }
        }.onFailure {
            Log.e(TAG, "PreferencesRepository unavailable during onCreate: ${it.message}")
        }
        Log.i(TAG, "NotiMind Lite Application Initialized")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory triggered with level: $level")

        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            AppIconCache.clearCache()
            val preferences = PreferencesRepository(this)
            if (preferences.securityManager.autoLockDb.value || preferences.securityManager.appLockEnabled.value) {
                DatabaseLockManager.lock()
                AppDatabase.resetInstance()
            }
        } else if (level == TRIM_MEMORY_RUNNING_LOW_LEVEL || level == TRIM_MEMORY_RUNNING_CRITICAL_LEVEL) {
            AppIconCache.clearCache()
            VectorEmbeddingHelper.clearCache()
            Log.w(TAG, "Running Low/Critical: Forced cache clear")
        } else if (level == TRIM_MEMORY_COMPLETE_LEVEL) {
            AppIconCache.clearCache()
            VectorEmbeddingHelper.clearCache()
            Log.w(TAG, "Trim Memory Complete: Final cache clear")
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
