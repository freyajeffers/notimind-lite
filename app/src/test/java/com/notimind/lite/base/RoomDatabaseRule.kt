package com.notimind.lite.base

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class RoomDatabaseRule : TestWatcher() {

    lateinit var database: AppDatabase
        private set

    lateinit var dao: NotificationDao
        private set

    override fun starting(description: Description?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
    }

    override fun finished(description: Description?) {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }
}
