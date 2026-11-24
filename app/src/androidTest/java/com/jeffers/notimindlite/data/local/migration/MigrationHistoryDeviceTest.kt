package com.jeffers.notimindlite.data.local.migration

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationHistoryDeviceTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migration_1_to_20_preservesLegacyNotification() {
        val testDbName = "migration-device-test-1-20.db"
        context.deleteDatabase(testDbName)
        val migrations = arrayOf(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17,
            AppDatabase.MIGRATION_17_18,
            AppDatabase.MIGRATION_18_19,
            AppDatabase.MIGRATION_19_20,
            AppDatabase.MIGRATION_20_21
        )
        MigrationTestFixtures.createVersionOneDatabase(context, testDbName).use { database ->
            database.execSQL(
                "INSERT INTO notifications " +
                    "(key, packageName, appName, title, content, postTime, isDismissed, isPersistent, priority) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "migration_v1_key", "com.migration.legacy", "Legacy App",
                    "Legacy title", "Legacy content", System.currentTimeMillis(), 0, 0, 0
                )
            )
        }

        val config = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(AppDatabase.DATABASE_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    migrations.forEach { it.migrate(db) }
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { database ->
            val cursor = database.query(
                "SELECT key, title FROM notifications WHERE key = ?",
                arrayOf<Any>("migration_v1_key")
            )
            cursor.use {
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            }
        }
        context.deleteDatabase(testDbName)
    }
}

private object MigrationTestFixtures {
    fun createVersionOneDatabase(context: Context, name: String): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE notifications (" +
                            "id INTEGER PRIMARY KEY NOT NULL, " +
                            "key TEXT NOT NULL, packageName TEXT NOT NULL, " +
                            "appName TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, " +
                            "postTime INTEGER NOT NULL, isDismissed INTEGER NOT NULL, " +
                            "isPersistent INTEGER NOT NULL, priority INTEGER NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }
}
