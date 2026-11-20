package com.jeffers.notimindlite.data.local.migration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.data.local.AppDatabase
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only diagnostics used while designing the 20 -> 21 rebuild migration.
 *
 * Reports whether the live database can satisfy the foreign keys declared by the
 * current schema, which decides whether the rebuild needs a data-repair step.
 */
@RunWith(AndroidJUnit4::class)
class MigrationReadinessDiagnosticsTest {
    @Test
    fun reportReferentialIntegrity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context).openHelper.readableDatabase

        db.query("SELECT COUNT(*) FROM notifications").use { c ->
            c.moveToFirst()
            println("DIAG notifications_total=${c.getLong(0)}")
        }
        val orphanPackageSql = "SELECT COUNT(*) FROM notifications n " +
            "WHERE NOT EXISTS (SELECT 1 FROM apps a WHERE a.packageName = n.packageName)"
        db.query(orphanPackageSql).use { c ->
            c.moveToFirst()
            println("DIAG orphan_package_rows=${c.getLong(0)}")
        }
        val orphanGroupSql = "SELECT COUNT(*) FROM notifications n WHERE n.groupKey IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM notification_groups g WHERE g.groupKey = n.groupKey)"
        db.query(orphanGroupSql).use { c ->
            c.moveToFirst()
            println("DIAG orphan_group_rows=${c.getLong(0)}")
        }
        db.query("SELECT COUNT(*) FROM apps").use { c ->
            c.moveToFirst()
            println("DIAG apps_total=${c.getLong(0)}")
        }
        db.query("SELECT COUNT(*) FROM notification_groups").use { c ->
            c.moveToFirst()
            println("DIAG groups_total=${c.getLong(0)}")
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'").use { c ->
            c.moveToFirst()
            println("DIAG triggers_total=${c.getLong(0)}")
        }
        val hasFk = db.query("SELECT COUNT(*) FROM pragma_foreign_key_list('notifications')").use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
        println("DIAG notifications_foreign_keys=$hasFk")
    }
}
