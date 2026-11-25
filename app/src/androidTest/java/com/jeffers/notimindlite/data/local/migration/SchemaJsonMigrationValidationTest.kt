package com.jeffers.notimindlite.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.notimindlite.data.local.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates every exported Room schema JSON against the migration that produces it.
 *
 * For each declared version `N` the test creates a database from the `N` schema JSON,
 * applies `MIGRATION_N_(N+1)`, and asks Room to validate the resulting database against
 * the `(N+1)` schema bundle. A mismatch in columns, indices, or identity hash is caught.
 *
 * Unlike [MigrationHistoryDeviceTest], which builds a legacy database from hand-written
 * DDL, this reads the real exported schemas, so a drifted JSON is detected.
 *
 * ## Known drift
 *
 * Two distinct classes, both verified by diffing Room's own validation output:
 *
 * 1. **Defaults.** Migrations emit `DEFAULT 0`/`DEFAULT 1`/`DEFAULT ''` (required when
 *    adding `NOT NULL` columns), but the entity fields declare no
 *    `@ColumnInfo(defaultValue = ...)`. The exported schema therefore records
 *    `defaultValue = undefined` while a migrated database carries a concrete default.
 *    The v21 rebuild closed this for `notifications`; it remains open for
 *    `notification_groups`, whose six `INTEGER NOT NULL DEFAULT 0` columns are created by
 *    the v18→19 migration but are not declared on `NotificationGroupEntity`. Closing that
 *    means rebuilding a parent table that `notifications` references by foreign key, which
 *    was judged not worth the risk: the defaults are never read, because Room always writes
 *    an explicit value on insert.
 * 2. **Index/column timing.** The older reconstructed pairs disagree about when an index or
 *    column appears, so a database built from the `N` schema does not match `N+1`.
 *
 * Regenerating the JSONs from git history does not clear either class: version 9 regenerated
 * byte-identical, so the current 8+ schemas already match history, and versions 1-7 never
 * existed in git history at all.
 *
 * Verified pairs, with the exact difference `describeDrift` reports:
 *
 * - `1 -> 2`, `2 -> 3`, `10 -> 11`: unexpected index — the migration creates
 *   `index_notifications_key` / `index_notifications_packageName` and the JSON omits it.
 * - `6 -> 7`: missing index `index_notifications_isPinned`.
 * - `8 -> 9`: missing `index_notifications_key`, plus unexpected
 *   `isDismissed`/`isPinned`/`postTime` indexes.
 * - `14 -> 15`: attributes differ — same column and index names, different definitions.
 * - `15 -> 16`: `backup_records` has an unexpected `encryptionKeyBase64` column.
 * - `16 -> 17`: five covering indexes missing (`isDismissed_dismissReason_dismissTime`,
 *   `isDismissed_isOngoing_postTime`, `isRead_isDismissed`,
 *   `packageName_isDismissed_postTime`, `syncStatus`) and two unexpected (`isRead`,
 *   `packageName_isDismissed`).
 * - `18 -> 19`: missing `index_notifications_packageName`.
 *
 * Several of these are harness artifacts rather than product defects. Real installs
 * accumulate indexes that earlier migrations created without the entities ever declaring
 * them, so a database built from the exported `N` schema starts from a different state
 * than a device that actually upgraded to `N`. `1 -> 2`, `2 -> 3`, `8 -> 9` and `10 -> 11`
 * all have this shape: the "unexpected" index is one a genuine v`N` device would already
 * carry.
 *
 * ## Why this runs in tests, not at runtime
 *
 * A previous attempt shipped a no-op `20 -> 21` migration on the assumption that Room only
 * compared the identity hash on open. It does not: after any migration Room validates the
 * published schema, and the mismatched `notifications` table aborted the upgrade and
 * crashed the app on first launch. These assertions reproduce that check in a place where
 * it cannot take a user's database with it.
 *
 * Supporting evidence that the 1-7 JSONs are reconstructions: they all carry the same
 * `identityHash` (`40c42c028c90…`) despite structurally different tables, whereas a genuine
 * Room export derives that hash from the schema.
 *
 * The baseline below keeps the gate meaningful: new drift fails, and fixing a listed pair
 * also fails so the entry is removed deliberately rather than silently rotting.
 *
 * ## What remains, precisely
 *
 * v21 declared the eleven column defaults for `notifications`, so a full v1→v21 chain no
 * longer diverges there. It still fails on `notification_groups`, whose six integer columns
 * (`activeCount`, `isDismissed`, `isPinned`, `lastUpdatedTime`, `latestPostTime`,
 * `notificationCount`) are created by migration with `DEFAULT 0` but carry no
 * `@ColumnInfo(defaultValue = "0")`. Adding them changes the schema hash, so it needs
 * either a version bump or amending the still-unreleased v21; until then a strict
 * end-to-end chain assertion cannot pass, which is why only the per-pair form exists.
 */
@RunWith(AndroidJUnit4::class)
class SchemaJsonMigrationValidationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun everyHistoricalSchema_migratesAndValidatesAgainstExportedJson() {
        val outcomes = mutableMapOf<String, String?>()
        for (migration in DECLARED_MIGRATIONS) {
            val from = migration.startVersion
            val pair = "$from->${migration.endVersion}"
            val databaseName = "schema-json-validation-$from.db"
            outcomes[pair] = try {
                helper.createDatabase(databaseName, from).close()
                helper.runMigrationsAndValidate(databaseName, migration.endVersion, true, migration)
                null
            } catch (failure: Throwable) {
                describeDrift(failure)
            } finally {
                InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
            }
        }

        val unexpected = outcomes.filter { (pair, error) -> error != null && pair !in KNOWN_SCHEMA_DRIFT }
        val newlyFixed = outcomes.filter { (pair, error) -> error == null && pair in KNOWN_SCHEMA_DRIFT }

        val report = buildString {
            if (unexpected.isNotEmpty()) {
                appendLine("New schema/migration drift detected:")
                unexpected.entries.forEach { appendLine("  ${it.key}: ${it.value}") }
            }
            if (newlyFixed.isNotEmpty()) {
                appendLine(
                    "Known drift now validates; remove from KNOWN_SCHEMA_DRIFT: " +
                        newlyFixed.keys.joinToString(", ")
                )
            }
        }
        assertTrue(report, report.isEmpty())
    }

    private companion object {
        /**
         * Room reports drift as a full "Expected: … Found: …" dump. Keeping only its first
         * line ("Migration didn't properly handle: notifications") hides what actually
         * differs, so reduce it to the column and index names that disagree.
         */
        fun describeDrift(failure: Throwable): String {
            val message = failure.message.orEmpty()
            val expected = message.substringAfter("Expected:", "").substringBefore("Found:")
            val found = message.substringAfter("Found:", "")
            val head = "${failure.javaClass.simpleName}: " +
                message.lineSequence().firstOrNull().orEmpty()
            if (expected.isBlank() || found.isBlank()) return head

            fun names(regex: Regex, source: String): Set<String> =
                regex.findAll(source).map { it.groupValues[1] }.toSet()

            val columnPattern =
                Regex("""Column\s*\{[^}]*?name = '([^']+)'""", RegexOption.DOT_MATCHES_ALL)
            val indexPattern =
                Regex("""Index\s*\{[^}]*?name = '([^']+)'""", RegexOption.DOT_MATCHES_ALL)

            val expectedColumns = names(columnPattern, expected)
            val foundColumns = names(columnPattern, found)
            val expectedIndexes = names(indexPattern, expected)
            val foundIndexes = names(indexPattern, found)

            val missingColumns = expectedColumns - foundColumns
            val extraColumns = foundColumns - expectedColumns
            val missingIndexes = expectedIndexes - foundIndexes
            val extraIndexes = foundIndexes - expectedIndexes
            val differences = buildList {
                if (missingColumns.isNotEmpty()) add("missing columns $missingColumns")
                if (extraColumns.isNotEmpty()) add("unexpected columns $extraColumns")
                if (missingIndexes.isNotEmpty()) add("missing indexes $missingIndexes")
                if (extraIndexes.isNotEmpty()) add("unexpected indexes $extraIndexes")
            }
            // Matching names mean a per-column attribute differs; that is the defaultValue
            // case, where migrations add NOT NULL columns with SQL defaults.
            return "$head (${differences.ifEmpty { listOf("attributes differ") }.joinToString("; ")})"
        }

        val KNOWN_SCHEMA_DRIFT = setOf(
            "1->2",
            "2->3",
            "6->7",
            "8->9",
            "10->11",
            "14->15",
            "15->16",
            "16->17",
            "18->19",
        )

        val DECLARED_MIGRATIONS = arrayOf(
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
            AppDatabase.MIGRATION_20_21,
        )
    }
}
