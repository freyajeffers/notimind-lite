package com.jeffers.notimindlite.migration

import com.notimind.lite.base.BaseRobolectricTest
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationOrchestratorTest : BaseRobolectricTest() {
  @Test
  fun atomicCutoverKeepsQuarantineBackup() {
    val directory = Files.createTempDirectory("notimind-cutover").toFile()
    val plaintext = File(directory, "plain.db").apply { writeText("plain") }
    val encrypted = File(directory, "encrypted.db").apply { writeText("encrypted") }
    val quarantine = File(directory, "plain.db.backup")

    try {
      val result = DatabaseMigrationOrchestrator().atomicCutover(plaintext, encrypted, quarantine)
      assertEquals(MigrationState.COMPLETE, result.state)
      assertTrue(plaintext.readText() == "encrypted")
      assertTrue(quarantine.readText() == "plain")
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun atomicCutoverRollsBackWhenPromotionFails() {
    val directory = Files.createTempDirectory("notimind-rollback").toFile()
    val plaintext = File(directory, "plain.db").apply { writeText("plain") }
    val encrypted = File(directory, "encrypted.db").apply { writeText("encrypted") }
    val quarantine = File(directory, "plain.db.backup")
    val failingOps = object : MigrationFileOps {
      override fun rename(source: File, destination: File) {
        if (source == encrypted) error("injected promotion failure")
        check(source.renameTo(destination))
      }
    }

    try {
      val result = DatabaseMigrationOrchestrator(failingOps).atomicCutover(plaintext, encrypted, quarantine)
      assertEquals(MigrationState.ROLLBACK_REQUIRED, result.state)
      assertTrue(plaintext.readText() == "plain")
      assertTrue(!quarantine.exists())
      assertTrue(encrypted.exists())
    } finally {
      directory.deleteRecursively()
    }
  }
}
