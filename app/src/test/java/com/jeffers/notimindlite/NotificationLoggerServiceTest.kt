package com.jeffers.notimindlite

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationLoggerServiceTest : BaseRobolectricTest() {

    @Test
    fun service_createsSuccessfullyWithoutNpe() {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        assertNotNull(service)
        serviceController.destroy()
    }

    @Test
    fun onNotificationPosted_suppressesSelfPackageNotifications() = runBlocking {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        // 1. Notification from app's own package (com.jeffers.notimindlite)
        val selfSbn = createMockStatusBarNotification(
            key = "com.jeffers.notimindlite|101|null|1001",
            packageName = context.packageName,
            title = "Self App Title",
            text = "Self App Content"
        )
        service.onNotificationPosted(selfSbn)
        Thread.sleep(100)

        assertEquals("Self package notifications must be ignored", 0, dao.getAllNotificationsList().size)

        // 2. Notification from external package (com.example.other)
        val externalSbn = createMockStatusBarNotification(
            key = "com.example.other|102|null|1002",
            packageName = "com.example.other",
            title = "External App Title",
            text = "External App Content"
        )
        service.onNotificationPosted(externalSbn)
        Thread.sleep(100)

        val list = dao.getAllNotificationsList()
        assertEquals("External package notification must be saved", 1, list.size)
        assertEquals("com.example.other", list[0].packageName)

        serviceController.destroy()
    }

    @Test
    fun onNotificationPosted_suppressesBlankTitleAndContentClutter() = runBlocking {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        val blankSbn = createMockStatusBarNotification(
            key = "com.example.app|103|null|1003",
            packageName = "com.example.app",
            title = "",
            text = ""
        )
        service.onNotificationPosted(blankSbn)
        Thread.sleep(100)

        assertEquals("Blank title and content notification clutter must be suppressed", 0, dao.getAllNotificationsList().size)

        serviceController.destroy()
    }

    @Test
    fun onNotificationPosted_suppressesSystemUsbDebuggingAndChargingClutter() = runBlocking {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        // USB debugging clutter notification from com.android.systemui
        val usbNotification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "USB debugging connected")
                putCharSequence(Notification.EXTRA_TEXT, "Tap to disable USB debugging")
            }
            priority = Notification.PRIORITY_MIN // -2
        }
        val usbSbn = StatusBarNotification(
            "com.android.systemui",
            "com.android.systemui",
            201,
            "com.android.systemui|201|null|2001",
            1000,
            0,
            0,
            usbNotification,
            Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(usbSbn)
        Thread.sleep(100)

        assertEquals("System USB debugging notification clutter must be suppressed", 0, dao.getAllNotificationsList().size)

        // System charging clutter notification from android with category "service"
        val chargingNotification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Charging connected")
                putCharSequence(Notification.EXTRA_TEXT, "Charging phone")
            }
            category = "service"
            priority = Notification.PRIORITY_MIN
        }
        val chargingSbn = StatusBarNotification(
            "android",
            "android",
            202,
            "android|202|null|2002",
            1000,
            0,
            0,
            chargingNotification,
            Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(chargingSbn)
        Thread.sleep(100)

        assertEquals("System charging notification clutter must be suppressed", 0, dao.getAllNotificationsList().size)

        serviceController.destroy()
    }

    @Test
    fun onNotificationPosted_suppressesMoreNotificationsSummaryClutter() = runBlocking {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        val summarySbn = createMockStatusBarNotification(
            key = "com.example.app|104|null|1004",
            packageName = "com.example.app",
            title = "3 more notifications",
            text = "Group summary"
        )
        service.onNotificationPosted(summarySbn)
        Thread.sleep(100)

        assertEquals("Summary clutter ('x more notifications') must be suppressed", 0, dao.getAllNotificationsList().size)

        serviceController.destroy()
    }
}

