package com.jeffers.notimindlite.util

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupNotaryClientTest {
    @Test
    fun fetchSignaturePostsJsonAndTrimsTheServerResponse() = runBlocking {
        val connection = FakeHttpURLConnection(statusCode = HttpURLConnection.HTTP_OK, response = "  signed-value\n")
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
    fun fetchSignatureRejectsNonOkStatusAndDisconnects() = runBlocking {
        val connection = FakeHttpURLConnection(statusCode = HttpURLConnection.HTTP_UNAVAILABLE, response = "ignored")

        val exception = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                BackupNotaryClient.fetchSignatureFromServer("sha-256-value", "test-integrity-token") { connection }
            }
        }

        assertTrue(exception.message.orEmpty().contains("Notary server returned 503"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun fetchSignatureDisconnectsWhenReadingResponseFails() = runBlocking {
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
        statusCode: Int,
        private val response: String,
        private val inputFailure: IOException? = null
    ) : HttpURLConnection(URL("https://unit-test.invalid")) {
        private val body = ByteArrayOutputStream()
        private val status = statusCode
        var disconnected = false
            private set

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getOutputStream(): OutputStream = body

        override fun getResponseCode(): Int = status

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
