package com.jeffers.notimindlite.migration

import androidx.room.Room
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationRunnerTest : BaseRobolectricTest() {

  @Test
  fun featureFlagOffReturnsNotRequired() = runTest {
    val state = MigrationRunner(context).runMigrationIfNeeded(featureFlag = false)
    assertEquals(MigrationState.NOT_REQUIRED, state)
  }

  @Test
  fun preflightReportsMissingPlaintextSourceWithoutWriting() {
    val preflight = MigrationRunner(context).preflight()
    assertEquals(false, preflight.plaintextExists)
    assertEquals(false, preflight.encryptedExists)
  }

  @Test
  fun preflightRecognizesPlaintextLegacyDatabaseBySQLiteHeader() {
    val databaseFile = context.getDatabasePath(AppDatabase.CE_DATABASE_NAME)
    databaseFile.parentFile?.mkdirs()
    databaseFile.writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(32))
    try {
      val preflight = MigrationRunner(context).preflight()

      assertEquals(true, preflight.plaintextExists)
      assertEquals(databaseFile.absolutePath, preflight.plaintextFile.absolutePath)
      assertEquals(false, preflight.encryptedExists)
    } finally {
      databaseFile.delete()
    }
  }

  @Test
  fun featureFlagOnStartsPreflight() = runTest {
    val state = MigrationRunner(context).runMigrationIfNeeded(featureFlag = true)
    assertEquals(MigrationState.PREFLIGHT, state)
  }

  @Test
  fun streamingCopyCopiesRows() = runTest {
    val src = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dst = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      src.notificationDao().insertNotification(
        NotificationEntity(key = "k1", packageName = "p", appName = "p", title = "t", content = "c", postTime = 1L)
      )

      assertEquals(1, src.notificationDao().getAllNotificationsList().size)
      MigrationRunner(context).performStreamingCopyForTest(src, dst, batchSize = 1)

      val list = dst.notificationDao().getAllNotificationsList()
      assertEquals("Copied rows: $list", 1, list.size)
      assertEquals("t", list[0].title)
    } finally {
      src.close()
      dst.close()
    }
  }
}
