package com.jeffers.notimindlite.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.PreferencesRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationActionExecutorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        NotificationLauncher.clearCache()
    }

    @After
    fun tearDown() {
        NotificationLauncher.clearCache()
    }

    @Test
    fun matchingRuleSelectsConfiguredActionForNotificationTrigger() {
        val preferences = PreferencesRepository(context)
        preferences.automationManager.setRules(listOf(AutoExecuteRule("chat", "com.chat", "urgent", 2)))
        val executor = NotificationActionExecutor(preferences)
        assertEquals(2, executor.matchingRule("com.chat", "Urgent: build failed")?.actionIndex)
        assertTrue(executor.matchingRule("com.mail", "Urgent") == null)
    }

    @Test
    fun notificationWithoutAutomationDoesNotTrigger() {
        val preferences = PreferencesRepository(context)
        preferences.automationManager.setAutoActOnNotification(false)
        preferences.automationManager.setRules(emptyList())
        val executor = NotificationActionExecutor(preferences)
        assertFalse(executor.executeOnNotification("missing", "com.chat", "hello"))
    }

    @Test
    fun matchingRule_ignoresDisabledRules() {
        val preferences = PreferencesRepository(context)
        val disabledRule = AutoExecuteRule(
            id = "disabled_rule",
            packageName = "com.chat",
            titleContains = "alert",
            actionIndex = 1,
            enabled = false
        )
        preferences.automationManager.setRules(listOf(disabledRule))
        val executor = NotificationActionExecutor(preferences)

        assertNull(executor.matchingRule("com.chat", "alert message"))
    }

    @Test
    fun matchingRule_blankPackageOrTitle_matchesWildcard() {
        val preferences = PreferencesRepository(context)
        val anyAppRule = AutoExecuteRule(
            id = "wildcard_app",
            packageName = "",
            titleContains = "security code",
            actionIndex = 0
        )
        val anyTitleRule = AutoExecuteRule(
            id = "wildcard_title",
            packageName = "com.bank",
            titleContains = "",
            actionIndex = 1
        )
        preferences.automationManager.setRules(listOf(anyAppRule, anyTitleRule))
        val executor = NotificationActionExecutor(preferences)

        val appMatch = executor.matchingRule("com.unknown.sender", "Your security code is 987")
        assertNotNull(appMatch)
        assertEquals("wildcard_app", appMatch?.id)

        val titleMatch = executor.matchingRule("com.bank", "Monthly Statement Ready")
        assertNotNull(titleMatch)
        assertEquals("wildcard_title", titleMatch?.id)
    }

    @Test
    fun executeOnNotification_withMatchingRule_triggersRegisteredAction() {
        val preferences = PreferencesRepository(context)
        val rule = AutoExecuteRule("exec_rule", "com.work.app", "ping", 0)
        preferences.automationManager.setRules(listOf(rule))
        val executor = NotificationActionExecutor(preferences)

        val key = "notif_trigger_key_1"
        val intent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        NotificationLauncher.registerActionIntent(key, 0, pi)

        val executed = executor.executeOnNotification(key, "com.work.app", "You got a ping")
        assertTrue(executed)
    }

    @Test
    fun executeOnNotification_withAutoActFallback_triggersDefaultAction() {
        val preferences = PreferencesRepository(context)
        preferences.automationManager.setRules(emptyList())
        preferences.automationManager.setAutoActOnNotification(true)
        val executor = NotificationActionExecutor(preferences)

        val key = "auto_act_key_1"
        val intent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        NotificationLauncher.registerActionIntent(key, 0, pi)

        val executed = executor.executeOnNotification(key, "com.other.app", "Regular update")
        assertTrue(executed)
    }

    @Test
    fun executeLongPress_replyAction_triggersRegisteredAction() {
        val preferences = PreferencesRepository(context)
        preferences.automationManager.setLongPressAction("reply")
        val executor = NotificationActionExecutor(preferences)

        val key = "long_press_reply_key"
        val intent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        NotificationLauncher.registerActionIntent(key, 0, pi)

        val executed = executor.executeLongPress(context, key, "com.chat")
        assertTrue(executed)
    }

    @Test
    fun executeLongPress_dismissAndOpenActions_executeSuccessfully() {
        val preferences = PreferencesRepository(context)
        val executor = NotificationActionExecutor(preferences)

        preferences.automationManager.setLongPressAction("dismiss")
        assertTrue(executor.executeLongPress(context, "dismiss_key", "com.chat"))

        preferences.automationManager.setLongPressAction("open")
        assertTrue(executor.executeLongPress(context, "open_key", "com.chat"))
    }
}
