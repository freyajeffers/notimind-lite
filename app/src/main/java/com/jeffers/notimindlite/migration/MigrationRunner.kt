package com.jeffers.notimindlite.migration

import android.content.Context
import android.os.StatFs
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jeffers.notimindlite.data.local.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Non-destructive migration gate. It only collects preflight diagnostics today;
 * copy/cutover must not be enabled until the SQLCipher key and rollback protocol
 * are implemented and covered by device tests.
 */
class MigrationRunner(
  private val context: Context,
  private val orchestrator: DatabaseMigrationOrchestrator = DatabaseMigrationOrchestrator()
) {

  suspend fun runMigrationIfNeeded(featureFlag: Boolean = false): MigrationState = withContext(Dispatchers.IO) {
    if (!featureFlag) return@withContext MigrationState.NOT_REQUIRED
    preflight()
    // Deliberately stop before copying or replacing any database files.
    MigrationState.PREFLIGHT
  }

  fun preflight(): MigrationPreflight {
    val databaseDir = context.getDatabasePath(AppDatabase.CE_DATABASE_NAME).parentFile
      ?: error("Unable to resolve database directory")
    val plaintext = File(databaseDir, "${AppDatabase.CE_DATABASE_NAME}.plaintext")
    val encrypted = context.getDatabasePath(AppDatabase.CE_DATABASE_NAME)
    val sourceBytes = plaintext.takeIf { it.exists() }?.length() ?: 0L
    val requiredBytes = (sourceBytes * 2L).coerceAtLeast(1L)
    val stat = StatFs(databaseDir.absolutePath)
    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
    return MigrationPreflight(plaintext, encrypted, plaintext.exists(), encrypted.exists(), availableBytes, requiredBytes)
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
    return orchestrator.atomicCutover(plaintextFile, encryptedTempFile, quarantineFile)
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
