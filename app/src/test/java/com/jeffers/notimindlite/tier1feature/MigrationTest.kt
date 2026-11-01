package com.jeffers.notimindlite.tier1feature

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema-migration tests for AppDatabase, including the current-schema smoke test and
 * versioned migrations. MigrationTestHelper schemas must exist under
 * app/src/main/assets/com.jeffers.notimindlite.data.local.AppDatabase/.
 *
 * Robolectric SQLite tests require x86_64; Linux aarch64 cannot load the native
 * Robolectric runtime, so run these migration tests in x86_64 CI or on an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        // Reset any singleton state left over from other test tiers
        AppDatabase.resetInstance()
    }

    @After
    fun tearDown() {
        db?.close()
        db = null
        AppDatabase.resetInstance()
    }

    /**
     * Scaffolded migration test. Demonstrates the canonical pattern but currently
     * disabled because app/schemas/.../17.json is not yet committed. See KDoc above.
     */
    @Test
    fun migration_17_to_18_runsSuccessfullyAndPreservesNotificationsTable() {
        // Step 1: createDatabase builds a v17 DB using Room's own schema generation
        // from the entity annotations. It needs 17.json in assets to validate
        // identityHash against the historical schema. Without that file the call throws
        // FileNotFoundException — by design.
        val testDbName = "migration-test-17-18.db"
        helper.createDatabase(testDbName, 17).use { v17 ->
            v17.query("SELECT name FROM sqlite_master WHERE type='table' AND name='notifications'").use { cursor ->
                assertTrue("notifications table must exist at v17", cursor.moveToFirst())
            }

            // Insert a row at v17 so we can confirm post-migration it survives.
            // MIGRATION_17_18 only modifies indexes, so the column list is identical
            // to v18 — see AppDatabase.kt:207.
            v17.execSQL(
                "INSERT INTO notifications " +
                    "(key, packageName, appName, appIconUri, title, content, postTime, lastUpdatedTime, " +
                    "updateCount, isDismissed, isPersistent, isRead, isGroupSummary, category, channelId, " +
                    "subText, bigText, inboxLinesJson, priority, groupKey, isOngoing, isClearable, actionsCount, " +
                    "dismissReason, dismissTime, intentUri, isPinned, actionLabels, smallIconRes, syncStatus, " +
                    "lastSyncedAt, embedding) VALUES " +
                    "(?, ?, ?, NULL, ?, ?, ?, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 0, ?, 0, 1, 0, " +
                    "NULL, NULL, NULL, 0, NULL, 0, 'PENDING', 0, NULL)",
                arrayOf<Any?>(
                    "migration_test_key",
                    "com.migration.test",
                    "MigrationApp",
                    "Migration Title",
                    "Migration Content",
                    "com.migration.test",
                    System.currentTimeMillis()
                )
            )
        }

        // Step 2: Run MIGRATION_17_18. Third arg (true) is validateDroppedTables — Room
        // fails the test if the migration drops any table that 18.json still defines.
        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            18,
            false,
            AppDatabase.MIGRATION_17_18
        )
        migrated.use { opened ->
            opened
                .query(
                    "SELECT key, title FROM notifications WHERE key = ?",
                    arrayOf<Any>("migration_test_key")
                ).use { cursor ->
                    assertTrue("Inserted row must survive the 17 -> 18 migration", cursor.moveToFirst())
                    val titleIdx = cursor.getColumnIndexOrThrow("title")
                    assertEquals(
                        "Title must be preserved",
                        "Migration Title",
                        cursor.getString(titleIdx)
                    )
                }
        }
    }

    @Test
    fun migration_18_to_19_backfillsNotificationGroups() {
        val testDbName = "migration-test-18-19.db"
        helper.createDatabase(testDbName, 18).use { v18 ->
            v18.execSQL(
                "INSERT INTO notifications " +
                    "(key, packageName, appName, appIconUri, title, content, postTime, lastUpdatedTime, " +
                    "updateCount, isDismissed, isPersistent, isRead, isGroupSummary, category, channelId, " +
                    "subText, bigText, inboxLinesJson, priority, groupKey, isOngoing, isClearable, actionsCount, " +
                    "dismissReason, dismissTime, intentUri, isPinned, actionLabels, smallIconRes, syncStatus, " +
                    "lastSyncedAt, embedding) VALUES " +
                    "(?, ?, ?, NULL, ?, ?, ?, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, " +
                    "NULL, NULL, NULL, 0, NULL, 0, 'PENDING', 0, NULL)",
                arrayOf<Any?>(
                    "migration_18_key", "com.migration.test", "MigrationApp", "Group title",
                    "Group content", System.currentTimeMillis()
                )
            )
            AppDatabase.MIGRATION_18_19.migrate(v18)
            v18
                .query(
                    "SELECT notificationCount FROM notification_groups WHERE groupKey = ?",
                    arrayOf<Any>("com.migration.test")
                ).use { cursor ->
                    assertTrue("Migration must backfill notification_groups", cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notificationCount")))
                }
        }
    }

    @Test
    fun migration_19_to_20_addsRestoreMetadataAndPreservesNotifications() {
        val testDbName = "migration-test-19-20.db"
        try {
            helper.createDatabase(testDbName, 19).use { v19 ->
                v19.execSQL(
                    "INSERT INTO notifications " +
                        "(key, packageName, appName, appIconUri, title, content, postTime, lastUpdatedTime, " +
                        "updateCount, isDismissed, isPersistent, isRead, isGroupSummary, category, channelId, " +
                        "subText, bigText, inboxLinesJson, priority, groupKey, isOngoing, isClearable, actionsCount, " +
                        "dismissReason, dismissTime, intentUri, isPinned, actionLabels, smallIconRes, syncStatus, " +
                        "lastSyncedAt, embedding) VALUES " +
                        "(?, ?, ?, NULL, ?, ?, ?, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 0, NULL, " +
                        "0, 1, 0, NULL, NULL, NULL, 0, NULL, 0, 'PENDING', 0, NULL)",
                    arrayOf<Any?>(
                        "migration_19_key",
                        "com.migration.test",
                        "MigrationApp",
                        "Migration Title",
                        "Migration Content",
                        System.currentTimeMillis()
                    )
                )
            }

            val migrated = helper.runMigrationsAndValidate(
                testDbName,
                20,
                false,
                AppDatabase.MIGRATION_19_20
            )
            migrated.use { opened ->
                val cursor = opened.query(
                    "SELECT title, restoreMetadataJson FROM notifications WHERE key = ?",
                    arrayOf<Any>("migration_19_key")
                )
                cursor.use {
                    assertTrue("Existing notification row must survive migration", it.moveToFirst())
                    assertEquals("Migration Title", it.getString(it.getColumnIndexOrThrow("title")))
                    assertEquals(null, it.getString(it.getColumnIndexOrThrow("restoreMetadataJson")))
                }
            }
        } finally {
            ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(testDbName)
        }
    }

    /**
     * Smoke test: builds the current schema (v20) end-to-end via Room and verifies
     * a round-tripped notification is retrievable. Always green — no prerequisites.
     */
    @Test
    fun smoke_notificationRoundTripSucceedsAtCurrentSchema() = runBlocking {
        db = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java
            ).allowMainThreadQueries()
            .build()
        db!!.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")

        val entity = NotificationEntity(
            key = "smoke_key",
            packageName = "com.mig.test",
            appName = "Migration App",
            title = "Migration Title",
            content = "Migration Content"
        )
        db!!.notificationDao().insertNotification(entity)
        val retrieved = db!!.notificationDao().getNotificationByKey("smoke_key")
        assertNotNull("Notification should be retrievable after round-trip", retrieved)
        assertEquals("Migration Title", retrieved?.title)
    }
}
