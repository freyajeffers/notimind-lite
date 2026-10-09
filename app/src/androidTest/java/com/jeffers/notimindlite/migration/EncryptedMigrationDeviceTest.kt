package com.jeffers.notimindlite.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device acceptance test for plaintext copy, cutover, and encrypted reopen. */
@RunWith(AndroidJUnit4::class)
class EncryptedMigrationDeviceTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun seededPlaintextDatabaseSurvivesEncryptedCutoverAndReopen() = runBlocking {
        System.loadLibrary("sqlcipher")
        val sourceName = "device_migration_plain.db"
        val encryptedName = "device_migration_encrypted.tmp"
        val quarantineName = "device_migration_plain.quarantine"
        val identityName = "device_migration_identity"
        val sourceFile = context.getDatabasePath(sourceName)
        val encryptedFile = context.getDatabasePath(encryptedName)
        val quarantineFile = context.getDatabasePath(quarantineName)
        val passphrase = SqlCipherKeyManager.getOrCreatePassphrase(context, identityName)
        val factory = SupportOpenHelperFactory(passphrase)
        var source: AppDatabase? = null
        var target: AppDatabase? = null

        try {
            source = Room.databaseBuilder(context, AppDatabase::class.java, sourceName).build()
            target = Room.databaseBuilder(context, AppDatabase::class.java, encryptedName)
                .openHelperFactory(factory)
                .build()
            source.notificationDao().insertNotification(
                NotificationEntity(
                    key = "device-migration-1",
                    packageName = "com.jeffers.notimindlite.test",
                    appName = "NotiMind test",
                    title = "Migration row",
                    content = "Physical device validation",
                    postTime = System.currentTimeMillis()
                )
            )

            val copyResult = DatabaseMigrationOrchestrator().copyAndVerify(
                source.openHelper.readableDatabase,
                target.openHelper.writableDatabase,
                batchSize = 1
            )
            assertEquals(MigrationState.VERIFYING, copyResult.state)
            source.close()
            target.close()
            source = null
            target = null

            val cutoverResult = DatabaseMigrationOrchestrator().atomicCutover(
                sourceFile,
                encryptedFile,
                quarantineFile
            )
            assertEquals(MigrationState.COMPLETE, cutoverResult.state)
            val finalized = DatabaseMigrationOrchestrator().finalizeSuccessfulCutover(cutoverResult)
            assertEquals(MigrationState.COMPLETE, finalized.state)
            assertTrue(sourceFile.isFile)
            assertTrue(!quarantineFile.exists())

            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, sourceName)
                .openHelperFactory(SupportOpenHelperFactory(
                    SqlCipherKeyManager.getOrCreatePassphrase(context, identityName)
                ))
                .build()
            try {
                assertEquals(1, reopened.notificationDao().getAllNotificationsList().size)
            } finally {
                reopened.close()
            }
        } finally {
            source?.close()
            target?.close()
            listOf(sourceFile, encryptedFile, quarantineFile).forEach(File::delete)
            context.deleteDatabase(sourceName)
            context.deleteDatabase(encryptedName)
        }
    }
}
