package com.jeffers.notimindlite.migration

import android.content.Context
import android.os.StatFs
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.EncryptedDatabaseFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates guarded legacy migration after the SQLCipher key and rollback protocol
 * have passed their validation gates.
 */
class MigrationRunner(
  private val context: Context,
  private val orchestrator: DatabaseMigrationOrchestrator = DatabaseMigrationOrchestrator()
) {

  suspend fun runMigrationIfNeeded(featureFlag: Boolean = false): MigrationState = withContext(Dispatchers.IO) {
    if (!featureFlag) return@withContext MigrationState.NOT_REQUIRED
    migrateLegacyDatabase()
  }

  fun preflight(): MigrationPreflight {
    return preflight(AppDatabase.CE_DATABASE_NAME)
  }

  /** Performs one guarded production migration for the active database identity. */
  @Suppress("ReturnCount", "LongMethod", "TooGenericExceptionCaught")
  fun migrateLegacyDatabase(databaseName: String = AppDatabase.CE_DATABASE_NAME): MigrationState {
    synchronized(MIGRATION_LOCK) {
      val diagnostics = preflight(databaseName)
      if (!diagnostics.plaintextExists) return MigrationState.NOT_REQUIRED
      if (diagnostics.encryptedExists) return MigrationState.RETRYABLE_FAILURE
      if (diagnostics.plaintextDatabase.name != databaseName) {
        return MigrationState.RETRYABLE_FAILURE
      }

      val directory = diagnostics.plaintextDatabase.parentFile
        ?: return MigrationState.RETRYABLE_FAILURE
      val encryptedTemp = File(directory, "$databaseName.tmp_encrypted")
      val quarantine = File(directory, "$databaseName.quarantine")
      if (encryptedTemp.exists() || quarantine.exists()) return MigrationState.RETRYABLE_FAILURE

      val targetFactory = EncryptedDatabaseFactory.openHelperFactory(context, databaseName)
        ?: return MigrationState.RETRYABLE_FAILURE
      val sourceHelper = plaintextHelper(diagnostics.plaintextDatabase.name)
      val target = Room.databaseBuilder(context, AppDatabase::class.java, encryptedTemp.name)
        .openHelperFactory(targetFactory)
        .build()
      return try {
        val sidecars = listOf("-wal", "-shm").map { suffix ->
          File(directory, "$databaseName$suffix") to
            File(directory, "$databaseName$suffix.quarantine")
        }
        val result = migrateOpenedDatabases(
          source = sourceHelper.writableDatabase,
          target = target.openHelper.writableDatabase,
          plaintextFile = diagnostics.plaintextDatabase,
          encryptedTempFile = encryptedTemp,
          quarantineFile = quarantine,
          closeDatabases = {
            sourceHelper.close()
            target.close()
          },
          executeCutover = false
        )
        if (result.state != MigrationState.CUTOVER_PENDING) {
          result.state
        } else {
          sourceHelper.close()
          target.close()
          val movedSidecars = mutableListOf<Pair<File, File>>()
          try {
            sidecars.filter { it.first.isFile }.forEach { (current, backup) ->
              check(current.renameTo(backup)) { "Unable to quarantine ${current.name}" }
              movedSidecars += current to backup
            }
            val cutover = orchestrator.atomicCutover(
              diagnostics.plaintextDatabase,
              encryptedTemp,
              quarantine
            )
            val finalized = orchestrator.finalizeSuccessfulCutover(
              cutover,
              movedSidecars.map { it.second }
            )
            if (finalized.state != MigrationState.COMPLETE) {
              movedSidecars.asReversed().forEach { (current, backup) -> backup.renameTo(current) }
            }
            finalized.state
          } catch (failure: Throwable) {
            movedSidecars.asReversed().forEach { (current, backup) -> backup.renameTo(current) }
            throw failure
          }
        }
      } finally {
        sourceHelper.close()
        target.close()
      }
    }
  }

  fun preflight(databaseName: String): MigrationPreflight {
    val databaseDir = context.getDatabasePath(databaseName).parentFile
      ?: error("Unable to resolve database directory")
    val plaintext = findLegacyPlaintextFile(databaseDir, databaseName)
    val encrypted = context.getDatabasePath(databaseName)
    val sourceBytes = plaintext.takeIf { it.exists() }?.length() ?: 0L
    val requiredBytes = (sourceBytes * 2L).coerceAtLeast(1L)
    val stat = StatFs(databaseDir.absolutePath)
    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
    return MigrationPreflight(
      plaintext,
      encrypted,
      plaintext.exists(),
      encrypted.isFile && !isPlaintextSQLite(encrypted),
      availableBytes,
      requiredBytes
    )
  }

  private fun findLegacyPlaintextFile(databaseDir: File, databaseName: String): File {
    val candidates = listOf(
      File(databaseDir, databaseName),
      File(databaseDir, "$databaseName.plaintext")
    )
    return candidates.firstOrNull { it.isFile && isPlaintextSQLite(it) }
      ?: candidates.last()
  }

  private fun isPlaintextSQLite(file: File): Boolean {
    if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
    val header = ByteArray(SQLITE_HEADER.size)
    val read = file.inputStream().use { input -> input.read(header) }
    return read == header.size && header.contentEquals(SQLITE_HEADER)
  }

  private companion object {
    private val MIGRATION_LOCK = Any()
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
  }

  private fun plaintextHelper(name: String): SupportSQLiteOpenHelper {
    val callback = object : SupportSQLiteOpenHelper.Callback(AppDatabase.DATABASE_VERSION) {
      override fun onCreate(db: SupportSQLiteDatabase) = Unit
      override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(name)
      .callback(callback)
      .build()
    return FrameworkSQLiteOpenHelperFactory().create(configuration)
  }

  fun migrateOpenedDatabases(
    source: SupportSQLiteDatabase,
    target: SupportSQLiteDatabase,
    plaintextFile: File,
    encryptedTempFile: File,
    quarantineFile: File,
    closeDatabases: () -> Unit,
    executeCutover: Boolean = false,
    batchSize: Int = 500
  ): MigrationResult {
    val copyResult = orchestrator.copyAndVerify(source, target, batchSize)
    if (copyResult.state != MigrationState.VERIFYING) return copyResult
    if (!executeCutover) return copyResult.copy(state = MigrationState.CUTOVER_PENDING)
    closeDatabases()
    val cutoverResult = orchestrator.atomicCutover(plaintextFile, encryptedTempFile, quarantineFile)
    return orchestrator.finalizeSuccessfulCutover(cutoverResult)
  }

  /**
   * Compatibility helper for the original in-memory migration tests. This method copies the
   * complete notifications rows in batches and never replaces or deletes source files.
   */
  fun performStreamingCopyForTest(sourceDb: AppDatabase, targetDb: AppDatabase, batchSize: Int = 50) {
    val src = sourceDb.openHelper.readableDatabase
    val dst = targetDb.openHelper.writableDatabase

    // Ensure target table exists (assumes schema-compatible AppDatabase)
    dst.execSQL("PRAGMA foreign_keys = OFF;")

    val countCursor = src.query("SELECT COUNT(*) FROM notifications")
    val total = if (countCursor.moveToFirst()) countCursor.getLong(0) else 0L
    countCursor.close()

    var offset = 0L
    while (offset < total) {
      val sel = src.query(
        "SELECT * FROM notifications LIMIT $batchSize OFFSET $offset"
      )

      dst.beginTransaction()
      try {
        while (sel.moveToNext()) {
          val values = android.content.ContentValues()
          for (columnIndex in 0 until sel.columnCount) {
            val columnName = sel.getColumnName(columnIndex)
            when (sel.getType(columnIndex)) {
              android.database.Cursor.FIELD_TYPE_NULL -> values.putNull(columnName)
              android.database.Cursor.FIELD_TYPE_INTEGER -> values.put(columnName, sel.getLong(columnIndex))
              android.database.Cursor.FIELD_TYPE_FLOAT -> values.put(columnName, sel.getDouble(columnIndex))
              android.database.Cursor.FIELD_TYPE_BLOB -> values.put(columnName, sel.getBlob(columnIndex))
              else -> values.put(columnName, sel.getString(columnIndex))
            }
          }
          dst.insert("notifications", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
        }
        dst.setTransactionSuccessful()
      } finally {
        dst.endTransaction()
        sel.close()
      }
      offset += batchSize
    }
  }
}
