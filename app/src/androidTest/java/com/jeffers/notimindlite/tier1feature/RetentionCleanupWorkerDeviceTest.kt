package com.jeffers.notimindlite.tier1feature

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.RetentionCleanupWorker
import com.jeffers.notimindlite.util.DatabaseLockManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetentionCleanupWorkerDeviceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: PreferencesRepository
    private var originalRetentionDays: Int? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstance()
        database = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        AppDatabase.setTestInstance(database)
        DatabaseLockManager.unlock()
        preferences = PreferencesRepository(context)
        originalRetentionDays = preferences.retentionDays.value
    }

    @After
    fun tearDown() {
        originalRetentionDays?.let(preferences::setRetentionDays)
        DatabaseLockManager.unlock()
        AppDatabase.resetInstance()
    }

    @Test
    fun finiteRetentionDeletesOnlyOldDismissedUnpinnedNotifications() = runBlocking {
        val now = System.currentTimeMillis()
        preferences.setRetentionDays(3)
        insertNotification("old-dismissed", now - 5 * MILLIS_PER_DAY, isDismissed = true)
        insertNotification("old-pinned", now - 5 * MILLIS_PER_DAY, isDismissed = true, isPinned = true)
        insertNotification("old-active", now - 5 * MILLIS_PER_DAY)
        insertNotification("recent-dismissed", now - MILLIS_PER_DAY, isDismissed = true)

        val result = TestListenableWorkerBuilder<RetentionCleanupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(setOf("old-pinned", "old-active", "recent-dismissed"), notificationKeys())
    }

    @Test
    fun retainAllPolicyLeavesOldDismissedNotificationsUntouched() = runBlocking {
        preferences.setRetentionDays(PreferencesRepository.RETAIN_ALL_DAYS)
        insertNotification("very-old-dismissed", 0L, isDismissed = true)

        val result = TestListenableWorkerBuilder<RetentionCleanupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(setOf("very-old-dismissed"), notificationKeys())
    }

    @Test
    fun invalidStoredZeroRetentionLeavesOldDismissedNotificationsUntouched() = runBlocking {
        writeStoredRetentionDays(0)
        insertNotification("zero-retention", 0L, isDismissed = true)

        val result = TestListenableWorkerBuilder<RetentionCleanupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(setOf("zero-retention"), notificationKeys())
    }

    @Test
    fun databaseLockFailureRequestsRetry() = runBlocking {
        preferences.setRetentionDays(3)
        DatabaseLockManager.lock()

        try {
            val result = TestListenableWorkerBuilder<RetentionCleanupWorker>(context).build().doWork()
            assertEquals(ListenableWorker.Result.retry(), result)
        } finally {
            DatabaseLockManager.unlock()
        }
    }

    private suspend fun insertNotification(
        key: String,
        postTime: Long,
        isDismissed: Boolean = false,
        isPinned: Boolean = false
    ) {
        database.notificationDao().insert(
            NotificationEntity(
                key = key,
                packageName = "com.example.retention",
                appName = "Retention Test",
                title = key,
                content = key,
                postTime = postTime,
                isDismissed = isDismissed,
                isPinned = isPinned
            )
        )
    }

    private fun writeStoredRetentionDays(days: Int) {
        val backing = context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(
                preferences.profileManager.activePreferencesName,
                Context.MODE_PRIVATE
            )
        check(backing.edit().putInt("config_retention_days", days).commit())
    }

    private fun notificationKeys(): Set<String> = database.openHelper.readableDatabase
        .query("SELECT `key` FROM notifications")
        .use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}
