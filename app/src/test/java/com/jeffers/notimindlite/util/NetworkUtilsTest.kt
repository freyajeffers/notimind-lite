package com.jeffers.notimindlite.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
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
    fun isInternetAvailable_whenNetworkHasInternetCapability_returnsTrue() {
        val mockedContext = mockk<Context>()
        val mockedManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { mockedContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockedManager
        every { mockedManager.activeNetwork } returns network
        every { mockedManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        assertTrue(NetworkUtils.isInternetAvailable(mockedContext))
    }

    @Test
    fun isInternetAvailable_whenInternetCapabilityIsMissing_returnsFalse() {
        val mockedContext = mockk<Context>()
        val mockedManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { mockedContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockedManager
        every { mockedManager.activeNetwork } returns network
        every { mockedManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        assertFalse(NetworkUtils.isInternetAvailable(mockedContext))
    }

    @Test
    fun isInternetAvailable_whenNetworkPlatformThrows_returnsFalse() {
        val failures = listOf(
            SecurityException("permission denied"),
            ClassCastException("service type"),
            IllegalStateException("offline")
        )
        failures.forEach { failure ->
            val mockedContext = mockk<Context>()
            every { mockedContext.getSystemService(Context.CONNECTIVITY_SERVICE) } throws failure

            assertFalse(NetworkUtils.isInternetAvailable(mockedContext))
        }
    }
}
