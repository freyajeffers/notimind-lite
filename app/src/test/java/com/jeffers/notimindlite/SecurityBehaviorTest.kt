package com.jeffers.notimindlite

import com.jeffers.notimindlite.util.BackupKeyWrap
import com.jeffers.notimindlite.util.DatabaseLockManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class SecurityBehaviorTest {
    @Test
    fun passphraseWrappingRoundTripsAndRejectsWrongPassphrase() {
        val generator = KeyGenerator.getInstance("AES").apply { init(256) }
        val dek = generator.generateKey()
        val salt = BackupKeyWrap.generateSalt()
        val wrapped = BackupKeyWrap.wrapDek(dek, BackupKeyWrap.deriveKek("correct".toCharArray(), salt))
        val restored = BackupKeyWrap.unwrapDek(
            wrapped.second,
            wrapped.first,
            BackupKeyWrap.deriveKek("correct".toCharArray(), salt),
        )
        assertArrayEquals(dek.encoded, restored.encoded)
        assertThrows(Exception::class.java) {
            BackupKeyWrap.unwrapDek(
                wrapped.second,
                wrapped.first,
                BackupKeyWrap.deriveKek("wrong".toCharArray(), salt),
            )
        }
    }

    @Test
    fun databaseLockBlocksAccessUntilUnlock() {
        DatabaseLockManager.unlock()
        DatabaseLockManager.requireUnlocked()
        DatabaseLockManager.lock()
        assertTrue(DatabaseLockManager.locked.value)
        assertThrows(IllegalStateException::class.java) { DatabaseLockManager.requireUnlocked() }
        DatabaseLockManager.unlock()
        DatabaseLockManager.requireUnlocked()
    }
}
