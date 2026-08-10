package com.notimind.lite.tier3_pairwise

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jeffers.notimindlite.receiver.BootReceiver
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

class BootReceiverDbPairwiseTest : BaseRobolectricTest() {

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun tc_T3_002_bootReceiverDbAndStatusBarDeduplication() = runTest {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm: ShadowNotificationManager = Shadows.shadowOf(notificationManager)

        val n1 = createDummyEntity(
            key = "k1",
            appName = "App 1",
            title = "Title 1",
            content = "Unique Content 1",
            isDismissed = false
        )
        val n2 = createDummyEntity(
            key = "k2",
            appName = "App 2",
            title = "Existing Active Title",
            content = "Existing Active Content",
            isDismissed = false
        )
        val n3 = createDummyEntity(
            key = "k3",
            appName = "App 3",
            title = "Title 3",
            content = "Content 3",
            isDismissed = true
        )

        dao.insertNotification(n1)
        dao.insertNotification(n2)
        dao.insertNotification(n3)

        val existingBuilder = NotificationCompat.Builder(context, BootReceiver.CHANNEL_ID_RESTORED)
            .setContentTitle("Existing Active Title")
            .setContentText("Existing Active Content")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        notificationManager.notify(1002, existingBuilder.build())

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val receiver = BootReceiver()

        receiver.onReceive(context, bootIntent)
        Thread.sleep(200)

        val postedNotifications = shadowNm.allNotifications
        val channelCreated = notificationManager.notificationChannels.any { it.id == BootReceiver.CHANNEL_ID_RESTORED }
        assertTrue("Restored notification channel must be created", channelCreated)
        assertTrue("Notifications should be posted", postedNotifications.isNotEmpty())

        val restoredN1 = postedNotifications.any { notif ->
            val title = notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val subText = notif.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            title.contains("Title 1") && subText == "Restored after Reboot"
        }
        assertTrue("N1 should be restored on boot with 'Restored after Reboot' subtext", restoredN1)
    }
}
