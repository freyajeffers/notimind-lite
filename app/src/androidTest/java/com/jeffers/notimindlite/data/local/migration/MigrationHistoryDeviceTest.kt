package com.jeffers.notimindlite.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationHistoryDeviceTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_1_to_20_preservesLegacyNotification() {
        val testDbName = "migration-device-test-1-20.db"
        helper.createDatabase(testDbName, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO notifications " +
                    "(key, packageName, appName, title, content, postTime, isDismissed, isPersistent, priority) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "migration_v1_key", "com.migration.legacy", "Legacy App",
                    "Legacy title", "Legacy content", System.currentTimeMillis(), 0, 0, 0
                )
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            20,
            false,
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
            AppDatabase.MIGRATION_19_20
        )
        migrated.use { database ->
            database
                .query(
                    "SELECT key, title FROM notifications WHERE key = ?",
                    arrayOf<Any>("migration_v1_key")
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Legacy title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                }
        }
    }
}
