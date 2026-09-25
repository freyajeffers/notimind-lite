package com.jeffers.notimindlite.migration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MigrationRunner: prototype for plaintext -> SQLCipher migration.
 * - featureFlag controls activation (read via BuildConfig or Remote Config in prod)
 * - methods are 'skeleton' and must be expanded with real DB and key APIs.
 */
class MigrationRunner(private val context: Context) {

  suspend fun runMigrationIfNeeded(featureFlag: Boolean = false): MigrationState = withContext(Dispatchers.IO) {
    if (!featureFlag) return@withContext MigrationState.NOT_REQUIRED

    // 1. Prefight checks (disk, locks, file presence)
    // 2. Create encrypted target DB via EncryptedDatabaseFactory
    // 3. Stream copy tables by page within transactions
    // 4. Verify counts and digests
    // 5. On success mark COMPLETE; on fail set RETRYABLE_FAILURE

    // Prototype returns PREFLIGHT to indicate the runner started.
    return@withContext MigrationState.PREFLIGHT
  }
}
