package com.jeffers.notimindlite.migration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.EncryptedDatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Debug-only, non-destructive migration trigger. */
class MigrationCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_MIGRATION_DRYRUN && intent.action != ACTION_RUN_MIGRATION_CUTOVER) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_RUN_MIGRATION_DRYRUN) {
                    runDryRun(context.applicationContext)
                } else {
                    runCutover(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun runDryRun(context: Context) {
        val databaseName = AppDatabase.CE_DATABASE_NAME
        val databaseDir = context.getDatabasePath(databaseName).parentFile
            ?: error("Unable to resolve database directory")
        val sourceFile = context.getDatabasePath(databaseName)
        val encryptedTempFile = File(databaseDir, "$databaseName.tmp_encrypted")
        val quarantineFile = File(databaseDir, "$databaseName.quarantine")
        check(sourceFile.isFile) { "Source database does not exist: ${sourceFile.name}" }
        check(!encryptedTempFile.exists()) { "Temporary target already exists; refusing to overwrite" }
        check(!quarantineFile.exists()) { "Quarantine file already exists; refusing to proceed" }

        val sourceHelper = plaintextHelper(context, sourceFile.name)
        val target = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            encryptedTempFile.name
        ).apply {
            EncryptedDatabaseFactory.openHelperFactory(context, encryptedTempFile.name)?.let(::openHelperFactory)
        }.build()

        try {
            val result = MigrationRunner(context).migrateOpenedDatabases(
                source = sourceHelper.writableDatabase,
                target = target.openHelper.writableDatabase,
                plaintextFile = sourceFile,
                encryptedTempFile = encryptedTempFile,
                quarantineFile = quarantineFile,
                closeDatabases = {
                    sourceHelper.close()
                    target.close()
                },
                executeCutover = false
            )
            File(context.filesDir, REPORT_NAME).writeText(
                "state=${result.state}\ncopiedRows=${result.copiedRows}\n" +
                    "verifiedRows=${result.verifiedRows}\ntarget=${encryptedTempFile.absolutePath}\n" +
                    "failure=${result.failure?.stackTraceToString().orEmpty()}\n"
            )
            Log.i(TAG, "Migration dry-run completed with state=${result.state}")
        } finally {
            sourceHelper.close()
            target.close()
        }
    }

    private fun runCutover(context: Context) {
        val databaseName = AppDatabase.CE_DATABASE_NAME
        val databaseDir = context.getDatabasePath(databaseName).parentFile
            ?: error("Unable to resolve database directory")
        val source = File(databaseDir, databaseName)
        val encryptedTemp = File(databaseDir, "$databaseName.tmp_encrypted")
        val quarantine = File(databaseDir, "$databaseName.quarantine")
        val sidecars = listOf("-wal", "-shm").map { suffix ->
            File(databaseDir, "$databaseName$suffix") to File(databaseDir, "$databaseName$suffix.quarantine")
        }
        check(source.isFile && encryptedTemp.isFile) { "Cutover files are missing" }
        check(!quarantine.exists() && sidecars.none { it.second.exists() }) {
            "A quarantine file already exists; refusing cutover"
        }

        val movedSidecars = mutableListOf<Pair<File, File>>()
        try {
            sidecars.filter { it.first.exists() }.forEach { (current, backup) ->
                check(current.renameTo(backup)) { "Unable to quarantine ${current.name}" }
                movedSidecars += current to backup
            }
            val result = DatabaseMigrationOrchestrator().atomicCutover(source, encryptedTemp, quarantine)
            if (result.state != MigrationState.COMPLETE) {
                movedSidecars.asReversed().forEach { (current, backup) -> backup.renameTo(current) }
            }
            File(context.filesDir, REPORT_NAME).writeText(
                "state=${result.state}\nquarantine=${quarantine.absolutePath}\n" +
                    "failure=${result.failure?.stackTraceToString().orEmpty()}\n"
            )
            Log.i(TAG, "Migration cutover completed with state=${result.state}")
        } catch (failure: Throwable) {
            movedSidecars.asReversed().forEach { (current, backup) -> backup.renameTo(current) }
            throw failure
        }
    }

    private fun plaintextHelper(context: Context, name: String): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(AppDatabase.DATABASE_VERSION) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
            override fun onUpgrade(
                db: androidx.sqlite.db.SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    companion object {
        const val ACTION_RUN_MIGRATION_DRYRUN = "com.jeffers.notimindlite.ACTION_RUN_MIGRATION_DRYRUN"
        const val ACTION_RUN_MIGRATION_CUTOVER = "com.jeffers.notimindlite.ACTION_RUN_MIGRATION_CUTOVER"
        const val REPORT_NAME = "migration_dryrun_report.txt"
        private const val TAG = "MigrationCommandReceiver"
    }
}
