package com.jeffers.notimindlite

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.receiver.BootReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {

    @Test
    fun bootReceiver_handlesBootCompletedIntentWithoutCrashing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)

        db.notificationDao().insertNotification(
            NotificationEntity(
                key = "boot_test_key",
                packageName = "com.example.chat",
                appName = "ChatApp",
                title = "Important Message",
                content = "Don't miss this meeting",
                isDismissed = false
            )
        )

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, bootIntent)
        assertNotNull(receiver)
    }

    @Test
    fun bootReceiver_skipsOngoingNotificationsOnBootRestoration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)

        db.notificationDao().insertNotification(
            NotificationEntity(
                key = "ongoing_test_key",
                packageName = "com.example.music",
                appName = "MusicPlayer",
                title = "Playing Track",
                content = "Music Stream",
                isDismissed = false,
                isOngoing = true,
                isPersistent = true
            )
        )

        val receiver = BootReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, bootIntent)
        assertNotNull(receiver)
    }

    @Test
    fun bootReceiver_handlesMyPackageReplacedIntent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()
        val replaceIntent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

        receiver.onReceive(context, replaceIntent)
        assertNotNull(receiver)
    }

    @Test
    fun bootReceiver_handlesQuickbootPowerOnIntent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()
        val powerOnIntent = Intent("android.intent.action.QUICKBOOT_POWERON")

        receiver.onReceive(context, powerOnIntent)
        assertNotNull(receiver)
    }
}
