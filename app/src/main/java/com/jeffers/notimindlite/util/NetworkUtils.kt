package com.jeffers.notimindlite.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utility for verifying device network connectivity state.
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Verifies if the device has an active internet connection.
     */
    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to evaluate network capabilities: ${e.message}", e)
            false
        }
    }
}
