package com.jeffers.notimindlite.util

import android.content.Context
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-local gate used to prevent database access while the app is backgrounded. */
object DatabaseLockManager {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    fun lock() {
        _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    fun lockIfEnabled(context: Context) {
        val preferences = PreferencesRepository(context.applicationContext)
        if (preferences.autoLockDb.value || preferences.appLockEnabled.value) lock()
    }

    fun requireUnlocked() {
        check(!_locked.value) { "Database is locked; bring the app to the foreground to unlock it" }
    }
}
