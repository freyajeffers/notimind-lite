package com.jeffers.notimindlite

import android.app.Application
import com.jeffers.notimindlite.util.DynamicClusterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotiMindApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize dynamic semantic clusters on application startup
        applicationScope.launch {
            DynamicClusterManager.initialize(this@NotiMindApp)
        }
    }
}
