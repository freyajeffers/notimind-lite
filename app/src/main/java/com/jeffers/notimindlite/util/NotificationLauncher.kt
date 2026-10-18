package com.jeffers.notimindlite.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object NotificationLauncher {
    private const val TAG = "NotificationLauncher"
    private val pendingIntentCache = ConcurrentHashMap<String, PendingIntent>()
    private val actionIntentCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, PendingIntent>>()

    fun registerPendingIntent(key: String, pendingIntent: PendingIntent?) {
        if (pendingIntent != null) {
            pendingIntentCache[key] = pendingIntent
        }
    }

    fun registerActionIntent(notifKey: String, actionIndex: Int, pendingIntent: PendingIntent?) {
        if (pendingIntent != null) {
            actionIntentCache.computeIfAbsent(notifKey) { ConcurrentHashMap() }[actionIndex] = pendingIntent
        }
    }

    fun unregisterPendingIntent(key: String) {
        pendingIntentCache.remove(key)
        actionIntentCache.remove(key)
    }

    fun getPendingIntentCacheSize(): Int = pendingIntentCache.size

    fun getActionIntentCacheSize(): Int = actionIntentCache.values.sumOf { it.size }

    fun clearCache() {
        pendingIntentCache.clear()
        actionIntentCache.clear()
    }

    fun isActionAvailable(notifKey: String, actionIndex: Int): Boolean {
        return actionIntentCache[notifKey]?.get(actionIndex) != null
    }

    fun triggerAction(notifKey: String, actionIndex: Int): Boolean {
        val actionIntent = actionIntentCache[notifKey]?.get(actionIndex)
        if (actionIntent != null) {
            try {
                
                actionIntent.send()
                return true
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "Action PendingIntent was canceled for: ${notifKey}_action_$actionIndex", e)
            }
        } else {
            Log.w(TAG, "No cached action PendingIntent found for: ${notifKey}_action_$actionIndex")
        }
        return false
    }

    fun launchNotification(context: Context, packageName: String, key: String, intentUri: String? = null) {
        val pendingIntent = pendingIntentCache[key]
        var launched = false

        // 1. Try live in-memory PendingIntent
        if (pendingIntent != null) {
            try {
                
                pendingIntent.send()
                launched = true
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "PendingIntent was canceled for key: $key. Attempting persistent intentUri fallback.", e)
            }
        }

        // 2. Try restored persistent intentUri
        if (!launched && !intentUri.isNullOrBlank()) {
            try {
                
                val parsedIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                if (packageName.isNotBlank()) {
                    parsedIntent.setPackage(packageName)
                }
                parsedIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (parsedIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(parsedIntent)
                    launched = true
                } else {
                    Log.w(TAG, "No activity resolved for parsed intentUri: $intentUri")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException launching parsed intentUri for package: $packageName", e)
            } catch (e: java.net.URISyntaxException) {
                Log.w(TAG, "Malformed intentUri for package: $packageName", e)
            }
        }

        // 3. Fallback to package main launch intent
        if (!launched) {
            try {
                
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    Log.w(TAG, "No launch intent found for package: $packageName")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while launching package main intent for: $packageName", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Invalid launch state for package: $packageName", e)
            }
        }
    }
}
