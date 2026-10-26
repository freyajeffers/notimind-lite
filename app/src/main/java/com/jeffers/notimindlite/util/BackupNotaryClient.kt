package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

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
        check(NetworkUtils.isInternetAvailable(context)) {
            "Active internet connection is required to notarize and create backup"
        }

        try {
            // 1. Request an Integrity Token from Google Play
            val integrityManager = IntegrityManagerFactory.create(context)
            val nonce = UUID.randomUUID().toString()
            val integrityToken = requestIntegrityToken(integrityManager, nonce)

            // 2. Send the hash and token to the Notary Server
            val signature = fetchSignatureFromServer(fileHash, integrityToken)

            signature
        } catch (e: java.net.SocketTimeoutException) {
            AppLogger.e(TAG, "Notary signature retrieval timed out: ${e.message}", e)
            throw SecurityException("Backup authorization failed: network timeout")
        } catch (e: java.io.IOException) {
            AppLogger.e(TAG, "Notary signature retrieval IO error: ${e.message}", e)
            throw SecurityException("Could not verify app identity due to IO failure. Backup authorization failed.")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Notary signature retrieval security failure: ${e.message}", e)
            throw e
        } catch (e: IllegalStateException) {
            AppLogger.e(TAG, "Notary signature retrieval illegal state: ${e.message}", e)
            throw SecurityException("Could not verify app identity. Backup authorization failed.")
        } catch (e: IllegalArgumentException) {
            AppLogger.e(TAG, "Notary signature retrieval invalid argument: ${e.message}", e)
            throw SecurityException("Could not verify app identity. Backup authorization failed.")
        } catch (e: ClassCastException) {
            AppLogger.e(TAG, "Notary signature retrieval response type failure: ${e.message}", e)
            throw SecurityException("Could not verify app identity. Backup authorization failed.")
        }
    }

    private suspend fun requestIntegrityToken(
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

    private suspend fun fetchSignatureFromServer(hash: String, token: String): String {
        val connection = URL(NOTARY_SERVER_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = NOTARY_CONNECT_TIMEOUT_MS
            connection.readTimeout = NOTARY_READ_TIMEOUT_MS

            val payload = org.json.JSONObject()
                .put("hash", hash)
                .put("token", token)
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Notary server returned ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: java.net.MalformedURLException) {
            Log.e(TAG, "Notary URL invalid", e)
            throw java.io.IOException("Notary URL invalid", e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Notary server unavailable or IO error", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
