package com.jeffers.notimindlite.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.PreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationActionExecutorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun matchingRuleSelectsConfiguredActionForNotificationTrigger() {
        val preferences = PreferencesRepository(context)
        preferences.setAutoExecuteRules(listOf(AutoExecuteRule("chat", "com.chat", "urgent", 2)))
        val executor = NotificationActionExecutor(preferences)
        assertEquals(2, executor.matchingRule("com.chat", "Urgent: build failed")?.actionIndex)
        assertTrue(executor.matchingRule("com.mail", "Urgent") == null)
    }

    @Test
    fun notificationWithoutAutomationDoesNotTrigger() {
        val preferences = PreferencesRepository(context)
        preferences.setAutoActOnNotification(false)
        preferences.setAutoExecuteRules(emptyList())
        val executor = NotificationActionExecutor(preferences)
        assertFalse(executor.executeOnNotification(context, "missing", "com.chat", "hello"))
    }
}
