package com.jeffers.notimindlite.tier1feature

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.util.BackupNotaryClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class BackupNotaryClientDeviceTest {
    @Test
    fun postsJsonAndReturnsTrimmedSignature() = runBlocking {
        val connection = FakeHttpURLConnection(HttpURLConnection.HTTP_OK, "  signed-value\n")
        var openedUrl: URL? = null

        val signature = BackupNotaryClient.fetchSignatureFromServer(
            hash = "sha-256-value",
            token = "test-integrity-token"
        ) { url ->
            openedUrl = url
            connection
        }

        assertEquals("https://api.notimind.lite/v1/internal/sign-hash", openedUrl.toString())
        assertEquals("POST", connection.requestMethod)
        assertTrue(connection.doOutput)
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertEquals(5_000, connection.connectTimeout)
        assertEquals(5_000, connection.readTimeout)
        assertEquals("sha-256-value", JSONObject(connection.requestBody()).getString("hash"))
        assertEquals("test-integrity-token", JSONObject(connection.requestBody()).getString("token"))
        assertEquals("signed-value", signature)
        assertTrue(connection.disconnected)
    }

    @Test
    fun rejectsNonOkStatusAndDisconnects() = runBlocking {
        val connection = FakeHttpURLConnection(HttpURLConnection.HTTP_UNAVAILABLE, "ignored")

        val exception = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                BackupNotaryClient.fetchSignatureFromServer("sha-256-value", "test-integrity-token") { connection }
            }
        }

        assertTrue(exception.message.orEmpty().contains("Notary server returned 503"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun disconnectsWhenReadingResponseFails() = runBlocking {
        val expected = IOException("input failed")
        val connection = FakeHttpURLConnection(
            statusCode = HttpURLConnection.HTTP_OK,
            response = "unused",
            inputFailure = expected
        )

        val actual = try {
            BackupNotaryClient.fetchSignatureFromServer("sha-256-value", "test-integrity-token") { connection }
            null
        } catch (failure: IOException) {
            failure
        }

        assertEquals(expected.message, actual?.message)
        assertTrue(connection.disconnected)
    }

    private class FakeHttpURLConnection(
        private val statusCode: Int,
        private val response: String,
        private val inputFailure: IOException? = null
    ) : HttpURLConnection(URL("https://unit-test.invalid")) {
        private val body = ByteArrayOutputStream()
        var disconnected = false
            private set

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getOutputStream(): OutputStream = body

        override fun getResponseCode(): Int = statusCode

        override fun getInputStream(): InputStream {
            val failure = inputFailure
            if (failure != null) {
                return object : InputStream() {
                    override fun read(): Int = throw failure
                }
            }
            return ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        }

        fun requestBody(): String = body.toString(Charsets.UTF_8.name())
    }
}
