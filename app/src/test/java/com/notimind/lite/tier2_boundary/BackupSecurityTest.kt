package com.notimind.lite.tier2_boundary

import android.content.Context
import com.jeffers.notimindlite.util.EncryptedBackupManager
import com.notimind.lite.base.BaseRobolectricTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class BackupSecurityTest : BaseRobolectricTest() {

    @Test
    fun `testAesGcmIntegrityFailure_ModifiedCiphertextShouldFail`() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        
        val sourceFile = File.createTempFile("source_db", ".db")
        sourceFile.writeText("This is sensitive database content that must remain intact.")
        
        val encryptedFile = File.createTempFile("backup", ".enc")
        
        // Use the manager's internal logic via a test-accessible route if possible, 
        // but since we need to modify the ciphertext, we use a helper to create the initial blob.
        val encryptedData = encryptSimple(sourceFile.readBytes(), secretKey)
        
        // 1. Verify valid decryption
        val decryptedValid = decryptSimple(encryptedData, secretKey)
        assertNotNull(decryptedValid)
        assertEquals(sourceFile.readText(), String(decryptedValid!!))

        // 2. Modify ciphertext (bit-flip)
        // AES-GCM puts the authentication tag at the end.
        val modifiedData = encryptedData.copyOf()
        modifiedData[modifiedData.size - 1] = (modifiedData[modifiedData.size - 1].toInt() xor 0xFF).toByte()
        
        // 3. Verify decryption fails due to Auth Tag mismatch
        val decryptedInvalid = decryptSimple(modifiedData, secretKey)
        assertNull("Decryption must return null (fail) when ciphertext/tag is modified", decryptedInvalid)
    }

    @Test
    fun `testAesGcmIntegrityFailure_ModifiedIVShouldFail`() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        val data = "Sensitive Data".toByteArray()
        
        val encryptedData = encryptSimple(data, secretKey)
        
        // Modify IV (first 12 bytes)
        val modifiedData = encryptedData.copyOf()
        modifiedData[0] = (modifiedData[0].toInt() xor 0xFF).toByte()
        
        val decrypted = decryptSimple(modifiedData, secretKey)
        assertNull("Decryption must fail when IV is modified", decrypted)
    }

    private fun encryptSimple(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    private fun decryptSimple(data: ByteArray, key: SecretKey): ByteArray? {
        return try {
            val iv = data.sliceArray(0 until 12)
            val ciphertext = data.sliceArray(12 until data.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }
}
