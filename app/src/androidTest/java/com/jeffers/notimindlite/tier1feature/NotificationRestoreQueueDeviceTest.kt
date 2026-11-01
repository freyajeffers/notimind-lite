package com.jeffers.notimindlite.tier1feature

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import com.jeffers.notimindlite.service.RestoredNotificationState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRestoreQueueDeviceTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() = runBlocking {
        database
            ?.notificationDao()
            ?.getAllRestoreMetadataRows()
            ?.mapNotNull { RestoredNotificationState.notificationId(it.restoreMetadataJson) }
            ?.distinct()
            ?.forEach(notificationManager::cancel)
        database?.close()
        database = null
    }

    @Test
    fun bulkRestoreQueuesOverflowAndDismissalPromotesNextQueuedNotification() = runBlocking {
        val db = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database = db
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        val dao = db.notificationDao()
        val now = System.currentTimeMillis()
        val records = (0 until 53).map { index ->
            NotificationEntity(
                id = 1_200_000_000L + index,
                key = "restore-queue-$index",
                packageName = context.packageName,
                appName = "Restore queue test",
                title = "Queued item $index",
                content = "Synthetic instrumentation notification $index",
                smallIconRes = R.drawable.ic_launcher_vector,
                isDismissed = true,
                dismissReason = 1,
                dismissTime = now + index,
                postTime = now + index
            )
        }
        dao.insertNotifications(records)

        val result = NotificationRestoreCoordinator.restoreRecentlyDismissed(context, dao)

        assertEquals(50, result.restoredCount)
        assertEquals(3, result.queuedCount)
        assertEquals(50, activeRestoreIds(dao).size)
        assertEquals(50, dao.getActiveNotificationsList().size)
        assertEquals(3, dao.getRecentlyDismissedList().size)
        assertEquals(3, dao.getQueuedRestoreNotifications().size)
        val activeSystemNotifications = notificationManager.activeNotifications
        assertEquals(50, activeSystemNotifications.size)
        assertTrue(
            activeSystemNotifications.all {
                it.notification.flags and Notification.FLAG_NO_CLEAR != 0 &&
                    it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0
            }
        )

        val activeRecord = dao.getAllNotificationsSync().first {
            RestoredNotificationState.notificationId(it.restoreMetadataJson) != null
        }
        assertNotNull(activeRecord.restoreMetadataJson)
        val dismissedNotificationId = requireNotNull(
            RestoredNotificationState.notificationId(activeRecord.restoreMetadataJson)
        )
        assertTrue(NotificationRestoreCoordinator.dismiss(context, dao, activeRecord))

        assertTrue(
            notificationManager.activeNotifications.none { it.id == dismissedNotificationId }
        )
        assertEquals(50, activeRestoreIds(dao).size)
        assertEquals(50, dao.getActiveNotificationsList().size)
        assertEquals(3, dao.getRecentlyDismissedList().size)
        assertEquals(2, dao.getQueuedRestoreNotifications().size)
        val dismissedRecord = dao.getAllNotificationsSync().first { it.id == activeRecord.id }
        assertNull(RestoredNotificationState.notificationId(dismissedRecord.restoreMetadataJson))
        assertEquals(53, dao.getAllNotificationsSync().size)
        assertEquals(52, activeRestoreIds(dao).size + dao.getQueuedRestoreNotifications().size)
    }

    private suspend fun activeRestoreIds(dao: com.jeffers.notimindlite.data.local.NotificationDao): Set<Int> =
        dao
            .getAllRestoreMetadataRows()
            .mapNotNull { RestoredNotificationState.notificationId(it.restoreMetadataJson) }
            .toSet()
}
