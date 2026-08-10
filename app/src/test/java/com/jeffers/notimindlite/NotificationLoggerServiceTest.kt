package com.jeffers.notimindlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.service.NotificationLoggerService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationLoggerServiceTest {

    @Test
    fun service_createsSuccessfullyWithoutNpe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        assertNotNull(service)
        serviceController.destroy()
    }
}
