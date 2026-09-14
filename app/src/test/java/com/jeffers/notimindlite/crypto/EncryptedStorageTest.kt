package com.jeffers.notimindlite.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.AEADBadTagException

class EncryptedStorageTest {

    @Test
    fun `encrypt decrypt roundtrip with derived key`() {
        val master = EncryptedStorage.generateMasterSecret()
        val pt = "Secret backup payload".toByteArray(Charsets.UTF_8)
        val aad = "backup-v1|meta".toByteArray(Charsets.UTF_8)

        val ct = EncryptedStorage.encrypt(master, pt, aad)
        val out = EncryptedStorage.decrypt(master, ct, aad)

        assertArrayEquals(pt, out)
    }

    @Test(expected = AEADBadTagException::class)
    fun `decrypt fails on tamper for derived key`() {
        val master = EncryptedStorage.generateMasterSecret()
        val pt = "Tamper check".toByteArray(Charsets.UTF_8)
        val ct = EncryptedStorage.encrypt(master, pt)
        val tampered = ct.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xAA).toByte()
        EncryptedStorage.decrypt(master, tampered)
    }

    @Test
    fun `different salts produce different keys`() {
        val master = EncryptedStorage.generateMasterSecret()
        val salt1 = ByteArray(16) { it.toByte() }
        val salt2 = ByteArray(16) { (it + 1).toByte() }
        val k1 = EncryptedStorage.deriveAesKey(master, salt1)
        val k2 = EncryptedStorage.deriveAesKey(master, salt2)
        assertFalse(k1.contentEquals(k2))
    }
}
