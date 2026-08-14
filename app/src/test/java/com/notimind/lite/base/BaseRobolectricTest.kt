package com.notimind.lite.base

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
abstract class BaseRobolectricTest {

    protected lateinit var context: Context
    protected lateinit var database: AppDatabase
    protected lateinit var dao: NotificationDao

    @Before
    open fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")
        AppDatabase.setTestInstance(database)
        dao = database.notificationDao()
    }

    @After
    open fun teardown() {
        AppDatabase.resetInstance()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    protected fun createDummyEntity(
        key: String = "test_key_${System.currentTimeMillis()}_${(1..1000).random()}",
        packageName: String = "com.example.app",
        appName: String = "Example App",
        title: String = "Test Title",
        content: String = "Test Content Body",
        postTime: Long = System.currentTimeMillis(),
        isDismissed: Boolean = false,
        isPersistent: Boolean = false
    ): NotificationEntity {
        return NotificationEntity(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            content = content,
            postTime = postTime,
            isDismissed = isDismissed,
            isPersistent = isPersistent
        )
    }

    @Suppress("DEPRECATION")
    protected fun createMockStatusBarNotification(
        key: String = "com.example.app|101|null|10001",
        packageName: String = "com.example.app",
        title: String = "Mock Title",
        text: String = "Mock Text",
        postTime: Long = System.currentTimeMillis(),
        isOngoing: Boolean = false,
        isClearable: Boolean = true
    ): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, title)
                putCharSequence(Notification.EXTRA_TEXT, text)
            }
            if (isOngoing) {
                flags = flags or Notification.FLAG_ONGOING_EVENT
            }
            if (!isClearable) {
                flags = flags or Notification.FLAG_NO_CLEAR
            }
        }

        return StatusBarNotification(
            packageName,
            packageName,
            101,
            key,
            1000,
            0,
            0,
            notification,
            Process.myUserHandle(),
            postTime
        )
    }
}
