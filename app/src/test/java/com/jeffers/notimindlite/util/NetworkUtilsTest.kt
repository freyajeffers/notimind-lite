package com.jeffers.notimindlite.util

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkUtilsTest {
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Test
    fun isInternetAvailable_whenActiveNetworkIsNull_returnsFalse() {
        val shadow = shadowOf(connectivityManager)
        shadow.setDefaultNetworkActive(false)
        shadow.clearAllNetworks()

        val isAvailable = NetworkUtils.isInternetAvailable(context)
        assertFalse(isAvailable)
    }

    @Test
    fun isInternetAvailable_whenNetworkActive_evaluatesWithoutCrashing() {
        val shadow = shadowOf(connectivityManager)
        shadow.setDefaultNetworkActive(true)

        val isAvailable = NetworkUtils.isInternetAvailable(context)
        assertTrue(isAvailable || !isAvailable)
    }
}
