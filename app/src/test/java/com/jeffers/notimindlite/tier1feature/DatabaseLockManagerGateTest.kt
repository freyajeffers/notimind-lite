package com.jeffers.notimindlite.tier1feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.DatabaseLockManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseLockManagerGateTest {
    @After
    fun resetLockState() {
        DatabaseLockManager.unlock()
    }

    @Test
    fun lockIfEnabledLocksOnlyWhenBothPreferencesAreEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferencesRepository(context)

        preferences.securityManager.setAutoLockDb(true)
        preferences.securityManager.setAppLockEnabled(true)
        DatabaseLockManager.lockIfEnabled(context)

        assertTrue(DatabaseLockManager.locked.value)
    }

    @Test
    fun lockIfEnabledDoesNotLockWhenAutoLockIsDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferencesRepository(context)

        preferences.securityManager.setAutoLockDb(false)
        preferences.securityManager.setAppLockEnabled(true)
        DatabaseLockManager.lockIfEnabled(context)

        assertFalse(DatabaseLockManager.locked.value)
    }

    @Test
    fun lockIfEnabledDoesNotLockWhenAppLockIsDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferencesRepository(context)

        preferences.securityManager.setAutoLockDb(true)
        preferences.securityManager.setAppLockEnabled(false)
        DatabaseLockManager.lockIfEnabled(context)

        assertFalse(DatabaseLockManager.locked.value)
    }
}
