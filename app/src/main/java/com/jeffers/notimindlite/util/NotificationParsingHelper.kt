package com.jeffers.notimindlite.util

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import org.json.JSONArray

class NotificationParsingHelper(private val packageManager: PackageManager) {
    data class TitleContent(val title: String, val content: String, val subText: String?, val bigText: String?)

    fun parseTitleAndContent(extras: Bundle?): TitleContent {
        val conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val title = if (rawTitle.isNotBlank()) rawTitle else (conversationTitle ?: "")
        val rawContent = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val summaryText = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        val content = when {
            rawContent.isNotBlank() -> rawContent
            !bigText.isNullOrBlank() -> bigText
            !summaryText.isNullOrBlank() -> summaryText
            else -> ""
        }
        return TitleContent(title, content, subText, bigText)
    }

    fun extractInboxLinesJson(extras: Bundle?): String? {
        return try {
            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!textLines.isNullOrEmpty()) JSONArray(textLines.map { it.toString() }).toString() else null
        } catch (e: ClassCastException) {
            Log.w("NotificationParsing", "Notification inbox lines were not CharSequence[]; trying String[]", e)
            val stringLines = extras?.getStringArray(Notification.EXTRA_TEXT_LINES)
            if (!stringLines.isNullOrEmpty()) JSONArray(stringLines.toList()).toString() else null
        }
    }

    fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("NotificationParsing", "Application package no longer exists: $packageName", e)
            packageName
        } catch (e: SecurityException) {
            Log.w("NotificationParsing", "Application label access denied for $packageName", e)
            packageName
        }
    }

    fun safeSmallIconRes(notification: Notification): Int {
        return try {
            notification.smallIcon?.resId ?: 0
        } catch (e: IllegalStateException) {
            Log.w("NotificationParsing", "Notification small icon is unavailable", e)
            0
        }
    }

    fun buildActionLabelsJson(actions: Array<android.app.Notification.Action>?): String? {
        if (actions.isNullOrEmpty()) return null
        val labels = mutableListOf<String>()
        actions.forEachIndexed { index, action ->
            labels.add(action.title?.toString() ?: "Action $index")
        }
        return JSONArray(labels).toString()
    }

    fun safeLaunchIntentUri(packageName: String): String? {
        return try {
            packageManager.getLaunchIntentForPackage(packageName)?.toUri(Intent.URI_INTENT_SCHEME)
        } catch (e: SecurityException) {
            Log.w("NotificationParsing", "Launch intent unavailable for package=$packageName", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w("NotificationParsing", "Invalid package name for launch intent: $packageName", e)
            null
        }
    }
}
