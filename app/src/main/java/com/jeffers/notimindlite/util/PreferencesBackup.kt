package com.jeffers.notimindlite.util

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Utilities to export and import app SharedPreferences as JSON files.
 * Designed for backups and for CI/dev automation tests.
 */
object PreferencesBackup {
    private const val PREFS_NAME = "notimind_lite_prefs"

    /**
     * Export all preferences to a JSON file at outPath. Returns the File on success.
     */
    fun exportPreferences(context: Context, outPath: String): File {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val jo = JSONObject()
        for ((k, v) in all) {
            when (v) {
                is Boolean -> jo.put(k, v)
                is Int -> jo.put(k, v)
                is Long -> jo.put(k, v)
                is Float -> jo.put(k, v.toDouble())
                is String -> jo.put(k, v)
                else -> jo.put(k, v.toString())
            }
        }
        val out = File(outPath)
        out.parentFile?.mkdirs()
        out.writeText(jo.toString(2))
        return out
    }

    /**
     * Import preferences from a JSON file. Existing keys are overwritten.
     */
    fun importPreferences(context: Context, inPath: String) {
        val file = File(inPath)
        if (!file.exists()) throw IllegalArgumentException("Import file not found: $inPath")
        val text = file.readText()
        val jo = JSONObject(text)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        jo.keys().forEach { key ->
            val v = jo.get(key)
            when (v) {
                is Boolean -> edit.putBoolean(key, v)
                is Int -> edit.putInt(key, v)
                is Long -> edit.putLong(key, v)
                is Double -> edit.putFloat(key, v.toFloat())
                is String -> edit.putString(key, v)
                else -> edit.putString(key, v.toString())
            }
        }
        edit.apply()
    }
}
