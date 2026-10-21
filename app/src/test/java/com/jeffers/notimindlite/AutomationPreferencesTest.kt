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

    @Test
    fun addRuleAndRemoveRule_updatesStateAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PreferencesRepository(context)
        repository.automationManager.setRules(emptyList())

        val rule1 = AutoExecuteRule("rule_add_1", "com.slack", "meeting", 0)
        val rule2 = AutoExecuteRule("rule_add_2", "com.discord", "game", 1)

        repository.automationManager.addRule(rule1)
        assertEquals(1, repository.automationManager.rules.value.size)
        assertEquals(
            "rule_add_1",
            repository.automationManager.rules.value[0]
                .id
        )

        repository.automationManager.addRule(rule2)
        assertEquals(2, repository.automationManager.rules.value.size)

        repository.automationManager.removeRule("rule_add_1")
        assertEquals(1, repository.automationManager.rules.value.size)
        assertEquals(
            "rule_add_2",
            repository.automationManager.rules.value[0]
                .id
        )

        repository.automationManager.removeRule("rule_add_2")
        assertTrue(
            repository.automationManager.rules.value
                .isEmpty()
        )
    }

    @Test
    fun setRules_deduplicatesById() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PreferencesRepository(context)

        val r1 = AutoExecuteRule("dup_id", "com.slack", "first", 0)
        val r2 = AutoExecuteRule("dup_id", "com.slack", "second", 1)

        repository.automationManager.setRules(listOf(r1, r2))
        assertEquals(1, repository.automationManager.rules.value.size)
        assertEquals(
            "first",
            repository.automationManager.rules.value[0]
                .titleContains
        )
    }

    @Test
    fun reload_reReadsFromPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        repo.automationManager.setAutoActOnNotification(true)
        repo.automationManager.setDefaultReplyMethod("copy")
        repo.automationManager.setLongPressAction("dismiss")

        repo.automationManager.reload()

        assertTrue(repo.automationManager.autoActOnNotification.value)
        assertEquals("copy", repo.automationManager.defaultReplyMethod.value)
        assertEquals("dismiss", repo.automationManager.longPressAction.value)
    }
}
