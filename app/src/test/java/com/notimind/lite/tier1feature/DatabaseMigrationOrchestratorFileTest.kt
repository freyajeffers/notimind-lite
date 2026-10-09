package com.notimind.lite.tier1feature

import com.jeffers.notimindlite.migration.DatabaseMigrationOrchestrator
import com.jeffers.notimindlite.migration.MigrationFileOps
import com.jeffers.notimindlite.migration.MigrationState
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationOrchestratorFileTest {
    @Test
    fun finalizeSuccessfulCutoverShredsPrimaryAndSidecarBackups() {
        val directory = Files.createTempDirectory("notimind-finalize").toFile()
        val plaintext = File(directory, "plain.db").apply { writeText("plain") }
        val encrypted = File(directory, "encrypted.db").apply { writeText("encrypted") }
        val quarantine = File(directory, "plain.db.quarantine")
        val sidecar = File(directory, "plain.db-wal.quarantine").apply { writeText("wal backup") }

        try {
            val orchestrator = DatabaseMigrationOrchestrator()
            val cutover = orchestrator.atomicCutover(plaintext, encrypted, quarantine)
            val result = orchestrator.finalizeSuccessfulCutover(cutover, listOf(sidecar))

            assertEquals(MigrationState.COMPLETE, result.state)
            assertEquals(null, result.backupFile)
            assertFalse(quarantine.exists())
            assertFalse(sidecar.exists())
            assertEquals("encrypted", plaintext.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedBackupDeletionLeavesRetryableResultAndBackupIntact() {
        val directory = Files.createTempDirectory("notimind-finalize-failure").toFile()
        val plaintext = File(directory, "plain.db").apply { writeText("plain") }
        val encrypted = File(directory, "encrypted.db").apply { writeText("encrypted") }
        val quarantine = File(directory, "plain.db.quarantine")
        val refusingOps = object : MigrationFileOps {
            override fun rename(source: File, destination: File) {
                check(source.renameTo(destination))
            }

            override fun secureDelete(file: File): Boolean = false
        }

        try {
            val orchestrator = DatabaseMigrationOrchestrator(refusingOps)
            val cutover = orchestrator.atomicCutover(plaintext, encrypted, quarantine)
            val result = orchestrator.finalizeSuccessfulCutover(cutover)

            assertEquals(MigrationState.RETRYABLE_FAILURE, result.state)
            assertTrue(quarantine.exists())
            assertTrue(result.failure?.message?.contains("plain.db.quarantine") == true)
        } finally {
            directory.deleteRecursively()
        }
    }
}
