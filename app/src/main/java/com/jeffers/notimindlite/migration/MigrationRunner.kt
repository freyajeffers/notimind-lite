package com.jeffers.notimindlite.migration

import android.content.Context
import android.os.StatFs
import com.jeffers.notimindlite.data.local.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Non-destructive migration gate. It only collects preflight diagnostics today;
 * copy/cutover must not be enabled until the SQLCipher key and rollback protocol
 * are implemented and covered by device tests.
 */
class MigrationRunner(private val context: Context) {

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

  /**
   * Prototype streaming copy for tests.
   * Copies a minimal set of columns from the notifications table in sourceDb into targetDb
   * in batches. This method is non-destructive and intended for unit/integration tests only.
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
        "SELECT `key`, packageName, title, content, postTime, isDismissed FROM notifications LIMIT $batchSize OFFSET $offset"
      )

      dst.beginTransaction()
      try {
        while (sel.moveToNext()) {
          val key = sel.getString(0)
          val pkg = sel.getString(1)
          val title = sel.getString(2)
          val content = sel.getString(3)
          val postTime = sel.getLong(4)
          val isDismissed = sel.getInt(5)

          // Insert into target (column list must match)
          dst.execSQL(
            "INSERT OR IGNORE INTO notifications (`key`, packageName, appName, title, content, postTime, isDismissed) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(key, pkg, pkg, title, content, postTime, isDismissed)
          )
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
