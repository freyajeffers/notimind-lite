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
        first.setAutoActOnNotification(true)
        first.setDefaultReplyMethod("copy")
        first.setLongPressAction("reply")
        first.setAutoExecuteRules(listOf(AutoExecuteRule(id = "r1", packageName = "com.chat", titleContains = "urgent", actionIndex = 2)))

        val second = PreferencesRepository(context)
        assertTrue(second.autoActOnNotification.value)
        assertEquals("copy", second.defaultReplyMethod.value)
        assertEquals("reply", second.longPressAction.value)
        assertEquals(1, second.autoExecuteRules.value.size)
        assertEquals(2, second.autoExecuteRules.value.single().actionIndex)
    }

    @Test
    fun invalidAutomationValuesUseSafeDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PreferencesRepository(context)
        repository.setDefaultReplyMethod("unknown")
        repository.setLongPressAction("unknown")
        assertEquals("inline", repository.defaultReplyMethod.value)
        assertEquals("open", repository.longPressAction.value)
        assertFalse(repository.autoExecuteRules.value.any { it.id.isBlank() })
    }
}
