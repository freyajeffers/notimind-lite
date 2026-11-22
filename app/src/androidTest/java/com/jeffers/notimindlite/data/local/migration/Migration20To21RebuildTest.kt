package com.jeffers.notimindlite.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jeffers.notimindlite.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates `MIGRATION_20_21`, which rebuilds `notifications` so the foreign keys declared
 * in v21 exist on upgraded databases.
 *
 * The fixture deliberately contains two kinds of dangling child rows, because upgraded
 * databases can hold them: `AppDao` deletes from `apps` without a cascade ever existing,
 * and group rows can be removed while notifications keep their `groupKey`. The migration is
 * expected to synthesise parent rows rather than drop history.
 */
@RunWith(AndroidJUnit4::class)
class Migration20To21RebuildTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val databaseName = "migration-20-21-rebuild.db"

    @Test
    fun migration20To21_preservesRowsAndRepairsOrphans() {
        helper.createDatabase(databaseName, 20).use { db ->
            db.execSQL(
                "INSERT INTO apps (packageName, appName, statusBarIconRes, firstSeenTime, " +
                    "lastSeenTime, syncStatus, lastSyncedAt) VALUES (?, ?, 0, ?, ?, ?, 0)",
                arrayOf<Any?>("com.example.known", "Known App", 1_000L, 2_000L, "PENDING_UPLOAD")
            )
            insertNotification(db, key = "n-known", packageName = "com.example.known", groupKey = null)
            insertNotification(db, key = "n-orphan-app", packageName = "com.orphan.app", groupKey = null)
            insertNotification(db, key = "n-orphan-group", packageName = "com.example.known", groupKey = "orphan-group")
        }

        helper.runMigrationsAndValidate(
            databaseName,
            21,
            true,
            AppDatabase.MIGRATION_20_21,
        ).use { db ->
            assertEquals(3L, scalar(db, "SELECT COUNT(*) FROM notifications"))
            assertEquals(
                "known notification must survive the rebuild",
                1L,
                scalar(db, "SELECT COUNT(*) FROM notifications WHERE `key` = 'n-known'")
            )
            assertEquals(
                "orphan package must get a synthesised apps row",
                1L,
                scalar(db, "SELECT COUNT(*) FROM apps WHERE packageName = 'com.orphan.app'")
            )
            assertEquals(
                "orphan group must get a synthesised notification_groups row",
                1L,
                scalar(db, "SELECT COUNT(*) FROM notification_groups WHERE groupKey = 'orphan-group'")
            )
            assertEquals(
                "foreign keys must be enforceable after the rebuild",
                0L,
                scalar(db, "SELECT COUNT(*) FROM pragma_foreign_key_check")
            )
            assertEquals(
                "FTS index must remain searchable and complete after the rebuild",
                3L,
                scalar(db, "SELECT COUNT(*) FROM notifications_fts WHERE notifications_fts MATCH 'Hello'")
            )
            assertTrue(
                "rebuilt table must declare both foreign keys",
                scalar(db, "SELECT COUNT(*) FROM pragma_foreign_key_list('notifications')") == 2L
            )
        }
    }

    private fun insertNotification(
        db: SupportSQLiteDatabase,
        key: String,
        packageName: String,
        groupKey: String?,
    ) {
        db.execSQL(
            "INSERT INTO notifications (`key`, packageName, appName, title, content, postTime, " +
                "lastUpdatedTime, updateCount, isDismissed, isPersistent, isRead, isGroupSummary, " +
                "priority, groupKey, isOngoing, isClearable, actionsCount, isPinned, smallIconRes, " +
                "syncStatus, lastSyncedAt) VALUES (?, ?, ?, ?, ?, ?, 0, 1, 0, 0, 0, 0, 0, ?, 0, 1, 0, 0, 0, ?, 0)",
            arrayOf<Any?>(
                key, packageName, "App", "Hello $key", "Body $key", 1_500L, groupKey, "PENDING_UPLOAD"
            )
        )
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
