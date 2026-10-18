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
        database = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
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

    protected data class DummyEntityParams(
        val key: String = "test_key_${System.currentTimeMillis()}_${(1..1000).random()}",
        val packageName: String = "com.example.app",
        val appName: String = "Example App",
        val title: String = "Test Title",
        val content: String = "Test Content Body",
        val postTime: Long = System.currentTimeMillis(),
        val isDismissed: Boolean = false,
        val isPersistent: Boolean = false
    )

    protected fun createDummyEntity(params: DummyEntityParams = DummyEntityParams()): NotificationEntity =
        NotificationEntity(
            key = params.key,
            packageName = params.packageName,
            appName = params.appName,
            title = params.title,
            content = params.content,
            postTime = params.postTime,
            isDismissed = params.isDismissed,
            isPersistent = params.isPersistent
        )

    protected data class MockStatusBarParams(
        val key: String = "com.example.app|101|null|10001",
        val packageName: String = "com.example.app",
        val title: String = "Mock Title",
        val text: String = "Mock Text",
        val postTime: Long = System.currentTimeMillis(),
        val isOngoing: Boolean = false,
        val isClearable: Boolean = true
    )

    protected fun createMockStatusBarNotification(
        params: MockStatusBarParams = MockStatusBarParams()
    ): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, params.title)
                putCharSequence(Notification.EXTRA_TEXT, params.text)
            }
            if (params.isOngoing) {
                flags = flags or Notification.FLAG_ONGOING_EVENT
            }
            if (!params.isClearable) {
                flags = flags or Notification.FLAG_NO_CLEAR
            }
        }

        return StatusBarNotification(
            params.packageName,
            params.packageName,
            101,
            params.key,
            1000,
            0,
            0,
            notification,
            Process.myUserHandle(),
            params.postTime
        )
    }
}
