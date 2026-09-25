package com.jeffers.notimindlite.migration

import java.io.File

/** Immutable diagnostics collected before any migration can write data. */
data class MigrationPreflight(
  val plaintextDatabase: File,
  val encryptedDatabase: File,
  val plaintextExists: Boolean,
  val encryptedExists: Boolean,
  val availableBytes: Long,
  val requiredBytes: Long,
) {
  val hasSufficientSpace: Boolean = availableBytes >= requiredBytes
  val canStart: Boolean = plaintextExists && !encryptedExists && hasSufficientSpace
}
