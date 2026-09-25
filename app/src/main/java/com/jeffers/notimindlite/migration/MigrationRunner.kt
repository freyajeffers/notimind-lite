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
}
