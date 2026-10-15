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
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Missing network permission when evaluating connectivity: ${e.message}", e)
            false
        } catch (e: ClassCastException) {
            AppLogger.w(TAG, "ConnectivityManager type mismatch: ${e.message}", e)
            false
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "Network subsystem in illegal state: ${e.message}", e)
            false
        }
    }
}
