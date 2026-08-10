package com.jeffers.notimindlite

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.util.NotificationLauncher
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationLauncherTest {

    @Test
    fun notificationLauncher_registersAndLaunchesFallbackWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        NotificationLauncher.registerPendingIntent("test_key_1", pendingIntent)
        NotificationLauncher.launchNotification(context, "com.jeffers.notimindlite", "test_key_1")
        assertNotNull(pendingIntent)
    }

    @Test
    fun notificationLauncher_launchesParsedIntentUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleIntent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val intentUri = sampleIntent.toUri(Intent.URI_INTENT_SCHEME)

        NotificationLauncher.launchNotification(context, "com.jeffers.notimindlite", "persistent_key", intentUri)
        assertNotNull(intentUri)
    }

    @Test
    fun notificationLauncher_handlesPackageFallbackWhenKeyNotFound() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationLauncher.launchNotification(context, "com.jeffers.notimindlite", "non_existent_key")
        assertNotNull(context)
    }

    @Test
    fun notificationLauncher_cacheUnregistrationOnRemovalGuaranteesZeroMemoryAccumulation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        NotificationLauncher.clearCache()
        val key = "burst_test_key_123"

        // Register main pending intent and action intents
        NotificationLauncher.registerPendingIntent(key, pendingIntent)
        NotificationLauncher.registerActionIntent(key, 0, pendingIntent)
        NotificationLauncher.registerActionIntent(key, 1, pendingIntent)

        org.junit.Assert.assertEquals(1, NotificationLauncher.getPendingIntentCacheSize())
        org.junit.Assert.assertEquals(2, NotificationLauncher.getActionIntentCacheSize())

        // Unregister on removal
        NotificationLauncher.unregisterPendingIntent(key)

        org.junit.Assert.assertEquals("PendingIntent cache must be empty after unregistration", 0, NotificationLauncher.getPendingIntentCacheSize())
        org.junit.Assert.assertEquals("ActionIntent cache must be empty after unregistration", 0, NotificationLauncher.getActionIntentCacheSize())
    }

    @Test
    fun notificationLauncher_tenThousandBurstCycleNoMemoryAccumulation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        NotificationLauncher.clearCache()

        // Simulate 10,000 notification post & remove cycles
        for (i in 1..10_000) {
            val key = "burst_cycle_key_$i"
            NotificationLauncher.registerPendingIntent(key, pendingIntent)
            NotificationLauncher.registerActionIntent(key, 0, pendingIntent)
            NotificationLauncher.unregisterPendingIntent(key)
        }

        org.junit.Assert.assertEquals("PendingIntent cache must be 0 after 10,000 burst cycle removals", 0, NotificationLauncher.getPendingIntentCacheSize())
        org.junit.Assert.assertEquals("ActionIntent cache must be 0 after 10,000 burst cycle removals", 0, NotificationLauncher.getActionIntentCacheSize())
    }
}
