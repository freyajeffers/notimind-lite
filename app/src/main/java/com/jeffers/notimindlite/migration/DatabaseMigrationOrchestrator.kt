package com.jeffers.notimindlite.migration

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/** Result of a migration attempt; plaintext is retained unless cutover succeeds. */
data class MigrationResult(
  val state: MigrationState,
  val copiedRows: Long,
  val verifiedRows: Long,
  val backupFile: File? = null,
  val failure: Throwable? = null
)

/**
 * Performs the guarded copy and file cutover for a pre-opened plaintext/SQLCipher pair.
 * The caller owns opening and closing both databases and must only pass closed files to
 * [atomicCutover]. No plaintext file is deleted by this class.
 */
class DatabaseMigrationOrchestrator(
  private val fileOps: MigrationFileOps = RealMigrationFileOps
) {
  fun copyAndVerify(
    source: SupportSQLiteDatabase,
    target: SupportSQLiteDatabase,
    batchSize: Int = DEFAULT_BATCH_SIZE
  ): MigrationResult {
    require(batchSize > 0) { "batchSize must be positive" }
    return try {
      val tables = userTables(source)
      var copied = 0L
      var verified = 0L
      target.beginTransaction()
      try {
        tables.forEach { table ->
          copied += copyTable(source, target, table, batchSize)
        }
        target.setTransactionSuccessful()
      } finally {
        target.endTransaction()
      }
      tables.forEach { table ->
        val sourceCount = countRows(source, table)
        val targetCount = countRows(target, table)
        check(sourceCount == targetCount) {
          "Row-count mismatch for $table: source=$sourceCount target=$targetCount"
        }
        verified += targetCount
      }
      MigrationResult(MigrationState.VERIFYING, copied, verified)
    } catch (failure: Throwable) {
      MigrationResult(MigrationState.ROLLBACK_REQUIRED, 0, 0, failure = failure)
    }
  }

  /**
   * Renames the closed plaintext database to a quarantine backup and promotes the closed
   * encrypted temporary database. If promotion fails, the original filename is restored.
   */
  fun atomicCutover(
    plaintextFile: File,
    encryptedTempFile: File,
    quarantineFile: File
  ): MigrationResult {
    if (!plaintextFile.isFile || !encryptedTempFile.isFile) {
      return MigrationResult(
        MigrationState.RETRYABLE_FAILURE,
        0,
        0,
        failure = IllegalStateException("Migration files are missing or are not regular files")
      )
    }
    if (quarantineFile.exists()) {
      return MigrationResult(
        MigrationState.RETRYABLE_FAILURE,
        0,
        0,
        failure = IllegalStateException("Quarantine file already exists: ${quarantineFile.name}")
      )
    }

    return try {
      fileOps.rename(plaintextFile, quarantineFile)
      try {
        fileOps.rename(encryptedTempFile, plaintextFile)
      } catch (promotionFailure: Throwable) {
        fileOps.rename(quarantineFile, plaintextFile)
        throw promotionFailure
      }
      MigrationResult(MigrationState.COMPLETE, 0, 0, backupFile = quarantineFile)
    } catch (failure: Throwable) {
      MigrationResult(MigrationState.ROLLBACK_REQUIRED, 0, 0, failure = failure)
    }
  }

  private fun userTables(database: SupportSQLiteDatabase): List<String> {
    val names = mutableListOf<String>()
    database.query(
      "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val name = cursor.getString(0)
        if (!name.startsWith("sqlite_") && name != ROOM_MASTER_TABLE && !name.contains("_fts")) {
          names += name
        }
      }
    }
    return names
  }

  private fun copyTable(
    source: SupportSQLiteDatabase,
    target: SupportSQLiteDatabase,
    table: String,
    batchSize: Int
  ): Long {
    val columns = tableColumns(source, table)
    check(columns.isNotEmpty()) { "Table has no columns: $table" }
    var copied = 0L
    var offset = 0L
    while (true) {
      val query = "SELECT * FROM ${quote(table)} LIMIT $batchSize OFFSET $offset"
      val inserted = source.query(query).use { cursor ->
        var count = 0
        while (cursor.moveToNext()) {
          val values = cursorValues(cursor, columns)
          target.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
          count++
        }
        count
      }
      copied += inserted
      if (inserted == 0) return copied
      offset += inserted
    }
  }

  private fun tableColumns(database: SupportSQLiteDatabase, table: String): List<String> {
    val columns = mutableListOf<String>()
    database.query("PRAGMA table_info(${quote(table)})").use { cursor ->
      while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
    }
    return columns
  }

  private fun cursorValues(cursor: Cursor, columns: List<String>): ContentValues {
    val values = ContentValues(columns.size)
    columns.forEachIndexed { index, column ->
      when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> values.putNull(column)
        Cursor.FIELD_TYPE_INTEGER -> values.put(column, cursor.getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> values.put(column, cursor.getDouble(index))
        Cursor.FIELD_TYPE_BLOB -> values.put(column, cursor.getBlob(index))
        else -> values.put(column, cursor.getString(index))
      }
    }
    return values
  }

  private fun countRows(database: SupportSQLiteDatabase, table: String): Long {
    return database.query("SELECT COUNT(*) FROM ${quote(table)}").use { cursor ->
      check(cursor.moveToFirst()) { "Unable to count $table" }
      cursor.getLong(0)
    }
  }

  private fun quote(identifier: String): String = "`${identifier.replace("`", "``")}`"

  companion object {
    private const val DEFAULT_BATCH_SIZE = 500
    private const val ROOM_MASTER_TABLE = "room_master_table"
  }
}

interface MigrationFileOps {
  fun rename(source: File, destination: File)
}

private object RealMigrationFileOps : MigrationFileOps {
  override fun rename(source: File, destination: File) {
    check(source.renameTo(destination)) {
      "Unable to rename ${source.absolutePath} to ${destination.absolutePath}"
    }
  }
}
