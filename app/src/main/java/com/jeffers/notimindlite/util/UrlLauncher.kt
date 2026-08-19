package com.jeffers.notimindlite.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Utility to handle the launching of external entities like URLs.
 */
object UrlLauncher {
    private const val TAG = "UrlLauncher"

    /**
     * Launches a URL in the default system browser.
     */
    fun launchUrl(context: Context, url: String) {
        try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch URL: $url", e)
        }
    }
}
