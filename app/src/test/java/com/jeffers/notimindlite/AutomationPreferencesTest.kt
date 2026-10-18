package com.jeffers.notimindlite

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
class AutomationPreferencesTest {
    @Test
    fun automationOptionsPersistAndRulesRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = PreferencesRepository(context)
        first.automationManager.setAutoActOnNotification(true)
        first.automationManager.setDefaultReplyMethod("copy")
        first.automationManager.setLongPressAction("reply")
        first.automationManager.setRules(
            listOf(
                AutoExecuteRule(
                    id = "r1",
                    packageName = "com.chat",
                    titleContains = "urgent",
                    actionIndex = 2
                )
            )
        )

        val second = PreferencesRepository(context)
        assertTrue(second.automationManager.autoActOnNotification.value)
        assertEquals("copy", second.automationManager.defaultReplyMethod.value)
        assertEquals("reply", second.automationManager.longPressAction.value)
        assertEquals(1, second.automationManager.rules.value.size)
        assertEquals(
            2,
            second.automationManager.rules.value
                .single()
                .actionIndex
        )
    }

    @Test
    fun invalidAutomationValuesUseSafeDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PreferencesRepository(context)
        repository.automationManager.setDefaultReplyMethod("unknown")
        repository.automationManager.setLongPressAction("unknown")
        assertEquals("inline", repository.automationManager.defaultReplyMethod.value)
        assertEquals("open", repository.automationManager.longPressAction.value)
        assertFalse(
            repository.automationManager.rules.value
                .any { it.id.isBlank() }
        )
    }
}
