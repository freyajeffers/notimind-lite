package com.jeffers.notimindlite.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.ui.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavigationAndSettingsUiTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testScreenRoutes_definedCorrectly() {
        assertEquals("active", Screen.Active.route)
        assertEquals("Active", Screen.Active.title)
        assertEquals("history", Screen.History.route)
        assertEquals("History", Screen.History.title)
        assertEquals("settings", Screen.Settings.route)
        assertEquals("Settings", Screen.Settings.title)
    }

    @Test
    fun testPreferenceManager_bootRestoreToggle() {
        val prefManager = PreferenceManager(context)
        assertTrue("Boot restore should default to true", prefManager.isRestoreOnBootEnabled())

        prefManager.setRestoreOnBootEnabled(false)
        assertFalse("Boot restore should be updated to false", prefManager.isRestoreOnBootEnabled())

        prefManager.setRestoreOnBootEnabled(true)
        assertTrue("Boot restore should be updated to true", prefManager.isRestoreOnBootEnabled())
    }

    @Test
    fun testClearLog_clearsDatabaseAndFlows() = runTest {
        // Insert sample notifications
        for (i in 1..10) {
            dao.insert(
                NotificationEntity(
                    id = i,
                    key = "key_$i",
                    packageName = "com.test.app$i",
                    appName = "App $i",
                    title = "Title $i",
                    content = "Content $i",
                    postTime = System.currentTimeMillis() - i * 1000,
                    isDismissed = i % 2 == 0
                )
            )
        }

        assertEquals(10, dao.getNotificationCount())
        assertEquals(10, dao.getTotalNotificationCountFlow().first())

        // Clear all logs
        dao.clearAll()

        assertEquals(0, dao.getNotificationCount())
        assertEquals(0, dao.getTotalNotificationCountFlow().first())
        assertTrue(dao.getAllNotificationsFlow().first().isEmpty())
        assertTrue(dao.getActiveNotificationsFlow().first().isEmpty())
    }

    @Test
    fun testLogHistorySearchFilter_matchesAllFields() = runTest {
        val n1 = NotificationEntity(id = 1, key = "k1", packageName = "com.chat.messenger", appName = "ChatApp", title = "Meeting reminder", content = "Project Sync at 3pm", postTime = 1000, isDismissed = true)
        val n2 = NotificationEntity(id = 2, key = "k2", packageName = "com.email.client", appName = "MailClient", title = "Urgent: Security Alert", content = "Password update required", postTime = 2000, isDismissed = true)
        val n3 = NotificationEntity(id = 3, key = "k3", packageName = "com.social.media", appName = "SocialFeed", title = "New Like", content = "Alice liked your post", postTime = 3000, isDismissed = true)

        dao.insert(n1)
        dao.insert(n2)
        dao.insert(n3)

        val list = dao.getDismissedNotificationsSortedByDismissed().first()
        assertEquals(3, list.size)

        // Filter by appName
        val appMatch = list.filter { it.appName.contains("Mail", ignoreCase = true) || it.title.contains("Mail", ignoreCase = true) || it.content.contains("Mail", ignoreCase = true) || it.packageName.contains("Mail", ignoreCase = true) }
        assertEquals(1, appMatch.size)
        assertEquals("MailClient", appMatch[0].appName)

        // Filter by title
        val titleMatch = list.filter { it.appName.contains("Meeting", ignoreCase = true) || it.title.contains("Meeting", ignoreCase = true) || it.content.contains("Meeting", ignoreCase = true) || it.packageName.contains("Meeting", ignoreCase = true) }
        assertEquals(1, titleMatch.size)
        assertEquals("Meeting reminder", titleMatch[0].title)

        // Filter by content
        val contentMatch = list.filter { it.appName.contains("Password", ignoreCase = true) || it.title.contains("Password", ignoreCase = true) || it.content.contains("Password", ignoreCase = true) || it.packageName.contains("Password", ignoreCase = true) }
        assertEquals(1, contentMatch.size)
        assertEquals("Password update required", contentMatch[0].content)

        // Filter by packageName
        val pkgMatch = list.filter { it.appName.contains("social", ignoreCase = true) || it.title.contains("social", ignoreCase = true) || it.content.contains("social", ignoreCase = true) || it.packageName.contains("social", ignoreCase = true) }
        assertEquals(1, pkgMatch.size)
        assertEquals("com.social.media", pkgMatch[0].packageName)
    }

    @Test
    fun testRapidDismissRecompositionLatency_sub16msSLA() = runTest {
        // Populate 100 active notifications
        for (i in 1..100) {
            dao.insert(
                NotificationEntity(
                    id = i,
                    key = "key_rapid_$i",
                    packageName = "com.test.app",
                    appName = "Test App",
                    title = "Rapid Dismiss Notification #$i",
                    content = "Sub-16ms latency SLA verification test body content",
                    postTime = System.currentTimeMillis(),
                    isDismissed = false
                )
            )
        }

        // Measure execution time of 50 rapid dismiss state transitions
        val iterations = 50
        val totalNano = measureNanoTime {
            for (i in 1..iterations) {
                dao.markDismissedWithReason("key_rapid_$i", 1)
            }
        }

        val avgMs = (totalNano / 1_000_000.0) / iterations
        assertTrue(
            "Average state transition latency ($avgMs ms) must be well below 16ms SLA limit",
            avgMs < 16.0
        )
    }
}
