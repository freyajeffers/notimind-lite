package com.jeffers.notimindlite.tier1feature

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRestoreMetadataMigrationInstrumentedTest {
    private val databaseName = "notification-restore-migration-test.db"
    private var databaseHelper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        databaseHelper?.close()
        databaseHelper = null
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migration19To20AddsMetadataColumnAndPreservesNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(19) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE notifications (" +
                        "id INTEGER PRIMARY KEY NOT NULL, `key` TEXT NOT NULL, title TEXT NOT NULL)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected database upgrade $oldVersion -> $newVersion")
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(databaseName)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        databaseHelper = helper
        val version19 = helper.writableDatabase
        version19.execSQL(
            "INSERT INTO notifications (`key`, title) VALUES (?, ?)",
            arrayOf<Any>("restore_migration_key", "Original title")
        )

        AppDatabase.MIGRATION_19_20.migrate(version19)
        val cursor = version19.query(
            "SELECT title, restoreMetadataJson FROM notifications WHERE `key` = ?",
            arrayOf<Any>("restore_migration_key")
        )
        cursor.use {
            assertTrue("The pre-migration row must survive", it.moveToFirst())
            assertEquals("Original title", it.getString(it.getColumnIndexOrThrow("title")))
            assertEquals(null, it.getString(it.getColumnIndexOrThrow("restoreMetadataJson")))
        }
    }
}
