package com.jeffers.notimindlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferenceManagerTest {

    @Test
    fun preferenceManager_defaultsToNoneAndPersistsState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager1 = PreferenceManager(context)

        assertEquals("ACTIVE", manager1.getExpandedSection())

        manager1.setExpandedSection("LOST")

        val manager2 = PreferenceManager(context)
        assertEquals("LOST", manager2.getExpandedSection())
    }
}
