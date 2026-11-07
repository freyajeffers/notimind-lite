package com.jeffers.notimindlite.util

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.jeffers.notimindlite.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * BackupNotaryClient handles the remote attestation and signing of backup hashes.
 * Instead of retrieving a secret salt, it sends a file hash to a secure server
 * which returns a cryptographically signed signature.
 */
object BackupNotaryClient {
    private const val TAG = "BackupNotaryClient"
    private const val NOTARY_SERVER_URL = "https://api.notimind.lite/v1/internal/sign-hash"
    private const val NOTARY_CONNECT_TIMEOUT_MS = 5_000
    private const val NOTARY_READ_TIMEOUT_MS = 5_000

    /**
     * Requests a secure signature for a given file hash from the NotiMind Notary Server.
     * Flow: Play Integrity Token -> Server Verification -> Signature Delivery.
     */
    suspend fun getSignature(context: Context, fileHash: String): String = withContext(Dispatchers.IO) {
        getSignatureWithDependencies(
            fileHash = fileHash,
            isOnline = { NetworkUtils.isInternetAvailable(context) },
            requestIntegrityToken = { nonce ->
                val integrityManager = IntegrityManagerFactory.create(context)
                requestIntegrityToken(integrityManager, nonce)
            },
            fetchSignature = { hash, token -> fetchSignatureFromServer(hash, token) }
        )
    }

    internal suspend fun getSignatureWithDependencies(
        fileHash: String,
        isOnline: () -> Boolean,
        requestIntegrityToken: suspend (String) -> String,
        fetchSignature: suspend (String, String) -> String
    ): String {
        check(isOnline()) {
            "Active internet connection is required to notarize and create backup"
        }

        try {
            val nonce = UUID.randomUUID().toString()
            val integrityToken = requestIntegrityToken(nonce)
            return fetchSignature(fileHash, integrityToken)
        } catch (e: java.net.SocketTimeoutException) {
            AppLogger.e(TAG, "Notary signature retrieval timed out: ${e.message}", e)
            throwSecurityFailure("Backup authorization failed: network timeout")
        } catch (e: java.io.IOException) {
            AppLogger.e(TAG, "Notary signature retrieval IO error: ${e.message}", e)
            throwSecurityFailure("Could not verify app identity due to IO failure. Backup authorization failed.")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Notary signature retrieval security failure: ${e.message}", e)
            rethrowSecurityFailure(e)
        } catch (e: IllegalStateException) {
            AppLogger.e(TAG, "Notary signature retrieval illegal state: ${e.message}", e)
            throwSecurityFailure("Could not verify app identity. Backup authorization failed.")
        } catch (e: IllegalArgumentException) {
            AppLogger.e(TAG, "Notary signature retrieval invalid argument: ${e.message}", e)
            throwSecurityFailure("Could not verify app identity. Backup authorization failed.")
        } catch (e: ClassCastException) {
            AppLogger.e(TAG, "Notary signature retrieval response type failure: ${e.message}", e)
            throwSecurityFailure("Could not verify app identity. Backup authorization failed.")
        }
    }

    private fun throwSecurityFailure(message: String): Nothing = throw SecurityException(message)

    private fun rethrowSecurityFailure(exception: SecurityException): Nothing = throw exception

    internal suspend fun requestIntegrityToken(
        manager: com.google.android.play.core.integrity.IntegrityManager,
        nonce: String
    ): String {
        val request = IntegrityTokenRequest
            .builder()
            .setNonce(nonce)
            .build()

        val response = manager.requestIntegrityToken(request).await()
        return response.token()
    }

    internal suspend fun fetchSignatureFromServer(
        hash: String,
        token: String,
        openConnection: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection }
    ): String {
        val connection = openConnection(URL(NOTARY_SERVER_URL))
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = NOTARY_CONNECT_TIMEOUT_MS
            connection.readTimeout = NOTARY_READ_TIMEOUT_MS

            val payload =
                JSONObject()
                    .put("hash", hash)
                    .put("token", token)
                    .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Notary server returned ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: java.net.MalformedURLException) {
            AppLogger.e(TAG, "Notary URL invalid", e)
            throw java.io.IOException("Notary URL invalid", e)
        } catch (e: java.io.IOException) {
            AppLogger.e(TAG, "Notary server unavailable or IO error", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
