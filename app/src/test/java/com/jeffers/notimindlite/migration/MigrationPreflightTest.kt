package com.jeffers.notimindlite.migration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MigrationPreflightTest {
    @Test
    fun hasSufficientSpace_trueWhenAvailableEqualsOrExceedsRequired() {
        val exact = MigrationPreflight(
            plaintextDatabase = File("plain.db"),
            encryptedDatabase = File("enc.db"),
            plaintextExists = true,
            encryptedExists = false,
            availableBytes = 1000L,
            requiredBytes = 1000L
        )
        assertTrue(exact.hasSufficientSpace)

        val excess = exact.copy(availableBytes = 2000L)
        assertTrue(excess.hasSufficientSpace)
    }

    @Test
    fun hasSufficientSpace_falseWhenAvailableIsLessThanRequired() {
        val insufficient = MigrationPreflight(
            plaintextDatabase = File("plain.db"),
            encryptedDatabase = File("enc.db"),
            plaintextExists = true,
            encryptedExists = false,
            availableBytes = 999L,
            requiredBytes = 1000L
        )
        assertFalse(insufficient.hasSufficientSpace)
    }

    @Test
    fun canStart_trueOnlyWhenPlaintextExistsEncryptedAbsentAndSpaceSufficient() {
        val valid = MigrationPreflight(
            plaintextDatabase = File("plain.db"),
            encryptedDatabase = File("enc.db"),
            plaintextExists = true,
            encryptedExists = false,
            availableBytes = 5000L,
            requiredBytes = 1000L
        )
        assertTrue(valid.canStart)
    }

    @Test
    fun canStart_falseWhenConditionsAreNotMet() {
        val base = MigrationPreflight(
            plaintextDatabase = File("plain.db"),
            encryptedDatabase = File("enc.db"),
            plaintextExists = true,
            encryptedExists = false,
            availableBytes = 5000L,
            requiredBytes = 1000L
        )

        assertFalse(base.copy(plaintextExists = false).canStart)
        assertFalse(base.copy(encryptedExists = true).canStart)
        assertFalse(base.copy(availableBytes = 500L).canStart)
    }
}
