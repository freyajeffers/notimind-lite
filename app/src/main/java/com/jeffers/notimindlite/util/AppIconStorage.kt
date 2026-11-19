package com.jeffers.notimindlite.util

import android.content.Context
import java.io.File

/** Durable, backup-excluded storage for notification app-icon snapshots. */
object AppIconStorage {
    private const val ICON_DIRECTORY = "app_icons"

    fun capturePath(context: Context, packageName: String): String {
        val durableDirectory = durableDirectory(context)
        val durableFile = File(durableDirectory, "$packageName.png")
        val legacyFile = File(context.cacheDir, "$ICON_DIRECTORY/$packageName.png")
        if (!durableFile.exists() && legacyFile.exists()) {
            runCatching {
                durableDirectory.mkdirs()
                legacyFile.copyTo(durableFile, overwrite = false)
            }.onFailure { error ->
                AppLogger.w("AppIconStorage", "Unable to migrate cached icon for $packageName", error)
            }
        }
        durableDirectory.mkdirs()
        return durableFile.absolutePath
    }

    fun resolveStoredPath(context: Context, storedPath: String): String {
        val legacyDirectory = File(context.cacheDir, ICON_DIRECTORY).absolutePath + File.separator
        if (!storedPath.startsWith(legacyDirectory)) return storedPath
        val packageFile = File(storedPath).nameWithoutExtension
        val durablePath = capturePath(context, packageFile)
        return if (File(durablePath).exists()) durablePath else storedPath
    }

    private fun durableDirectory(context: Context): File =
        File(context.filesDir, ICON_DIRECTORY)
}
