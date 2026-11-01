package com.jeffers.notimindlite.tier1feature

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.util.BackupNotaryClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupNotarySignatureFlowDeviceTest {
    @Test
    fun successfulFlowPassesNonceHashAndTokenBetweenStages() = runBlocking {
        var requestedNonce: String? = null
        var requestedHash: String? = null
        var requestedToken: String? = null

        val signature = BackupNotaryClient.getSignatureWithDependencies(
            fileHash = "backup-hash",
            isOnline = { true },
            requestIntegrityToken = { nonce ->
                requestedNonce = nonce
                "integrity-token"
            },
            fetchSignature = { hash, token ->
                requestedHash = hash
                requestedToken = token
                "signed-backup-hash"
            }
        )

        UUID.fromString(requestedNonce)
        assertEquals("backup-hash", requestedHash)
        assertEquals("integrity-token", requestedToken)
        assertEquals("signed-backup-hash", signature)
    }

    @Test
    fun offlinePreconditionStopsBeforeRequestingIntegrityOrSignature() = runBlocking {
        var downstreamCalled = false
        val exception = captureFailure {
            BackupNotaryClient.getSignatureWithDependencies(
                fileHash = "backup-hash",
                isOnline = { false },
                requestIntegrityToken = {
                    downstreamCalled = true
                    "integrity-token"
                },
                fetchSignature = { _, _ ->
                    downstreamCalled = true
                    "signature"
                }
            )
        }

        assertTrue(exception is IllegalStateException)
        assertEquals(
            "Active internet connection is required to notarize and create backup",
            exception.message
        )
        assertFalse(downstreamCalled)
    }

    @Test
    fun mapsTimeoutIoAndUnexpectedFailuresToSecurityErrors() = runBlocking {
        val cases = listOf(
            SocketTimeoutException("timeout") to "Backup authorization failed: network timeout",
            IOException("io") to "Could not verify app identity due to IO failure. Backup authorization failed.",
            IllegalStateException("state") to "Could not verify app identity. Backup authorization failed.",
            IllegalArgumentException("argument") to "Could not verify app identity. Backup authorization failed.",
            ClassCastException("response") to "Could not verify app identity. Backup authorization failed."
        )

        cases.forEach { (cause, expectedMessage) ->
            val exception = captureFailure {
                BackupNotaryClient.getSignatureWithDependencies(
                    fileHash = "backup-hash",
                    isOnline = { true },
                    requestIntegrityToken = { throw cause },
                    fetchSignature = { _, _ -> error("Signature stage must not be reached") }
                )
            }

            assertTrue(exception is SecurityException)
            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun preservesSecurityFailuresWithoutWrapping() = runBlocking {
        val expected = SecurityException("integrity rejected")
        val actual = captureFailure {
            BackupNotaryClient.getSignatureWithDependencies(
                fileHash = "backup-hash",
                isOnline = { true },
                requestIntegrityToken = { throw expected },
                fetchSignature = { _, _ -> error("Signature stage must not be reached") }
            )
        }

        assertSame(expected, actual)
    }

    private suspend fun captureFailure(block: suspend () -> String): Throwable =
        try {
            block()
            AssertionError("Expected signature request to fail")
        } catch (failure: Throwable) {
            failure
        }
}
