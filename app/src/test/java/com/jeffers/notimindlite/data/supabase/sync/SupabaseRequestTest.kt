package com.jeffers.notimindlite.data.sync

import com.jeffers.notimindlite.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class SupabaseRequestTest {
    @Test
    fun requestWithConnection_sendsConfiguredJsonRequestAndDisconnects() {
        val path = "/rest/v1/notifications"
        val connection = TestHttpURLConnection(statusCode = 201, responseBody = "created")
        var openedUrl: URL? = null

        val response = requestWithConnection(
            method = "POST",
            path = path,
            token = "unit-test-token",
            body = "{\"record\":true}",
            prefer = "return=representation"
        ) { url ->
            openedUrl = url
            connection
        }

        assertEquals(BuildConfig.SUPABASE_URL + path, openedUrl.toString())
        assertEquals("created", response)
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer unit-test-token", connection.requestHeaders["Authorization"])
        assertTrue(connection.requestHeaders["apikey"]?.isNotBlank() == true)
        assertEquals("application/json", connection.requestHeaders["Content-Type"])
        assertEquals("return=representation", connection.requestHeaders["Prefer"])
        assertEquals("{\"record\":true}", connection.requestBody.toString(Charsets.UTF_8.name()))
        assertEquals(SUPABASE_HTTP_CONNECT_TIMEOUT_MILLIS, connection.connectTimeout)
        assertEquals(SUPABASE_HTTP_READ_TIMEOUT_MILLIS, connection.readTimeout)
        assertTrue(connection.doOutput)
        assertTrue(connection.disconnected)
    }

    @Test
    fun requestWithConnection_nonSuccessResponseThrowsBodyAndDisconnects() {
        val connection = TestHttpURLConnection(statusCode = 403, errorBody = "forbidden")

        val exception = assertThrows(IllegalStateException::class.java) {
            requestWithConnection(method = "DELETE", path = "/rest/v1/notifications", token = "unit-test-token") {
                connection
            }
        }

        assertTrue(exception.message.orEmpty().contains("Supabase HTTP 403: forbidden"))
        assertFalse(connection.doOutput)
        assertFalse(connection.requestHeaders.containsKey("Prefer"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun requestWithConnection_nullErrorStreamUsesEmptyBodyAndDisconnects() {
        val connection = TestHttpURLConnection(statusCode = 500)

        val exception = assertThrows(IllegalStateException::class.java) {
            requestWithConnection(method = "GET", path = "/rest/v1/notifications", token = "unit-test-token") {
                connection
            }
        }

        assertTrue(exception.message.orEmpty().contains("Supabase HTTP 500: "))
        assertTrue(connection.disconnected)
    }

    @Test
    fun requestWithConnection_statusBelowSuccessRangeThrowsAndDisconnects() {
        val connection = TestHttpURLConnection(statusCode = 199, errorBody = "early response")

        val exception = assertThrows(IllegalStateException::class.java) {
            requestWithConnection(method = "GET", path = "/rest/v1/notifications", token = "unit-test-token") {
                connection
            }
        }

        assertTrue(exception.message.orEmpty().contains("Supabase HTTP 199: early response"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun requestWithConnection_upperSuccessBoundaryAcceptsEmptyBody() {
        val connection = TestHttpURLConnection(statusCode = 299)

        val response = requestWithConnection(
            method = "GET",
            path = "/rest/v1/notifications",
            token = "unit-test-token"
        ) {
            connection
        }

        assertEquals("", response)
        assertTrue(connection.disconnected)
    }

    private class TestHttpURLConnection(
        private val statusCode: Int,
        private val responseBody: String = "",
        private val errorBody: String? = null
    ) : HttpURLConnection(URL("https://example.invalid")) {
        val requestHeaders = mutableMapOf<String, String>()
        val requestBody = ByteArrayOutputStream()
        var disconnected = false
            private set

        override fun connect() {
            connected = true
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false

        override fun getResponseCode() = statusCode

        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray())

        override fun getErrorStream(): InputStream? = errorBody?.let { ByteArrayInputStream(it.toByteArray()) }

        override fun getOutputStream(): OutputStream = requestBody

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }
    }
}
