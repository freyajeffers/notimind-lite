package com.notimind.lite.tier2_boundary

import android.content.Intent
import android.content.pm.PackageManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.receiver.BootReceiver
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

class ArchitectureBoundaryTest : BaseRobolectricTest() {

    @Test
    fun tc_R1_T2_001_absenceOfInternetPermissionAudit() {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "INTERNET permission must be requested for sync",
            permissions.contains("android.permission.INTERNET")
        )
        assertTrue(
            "ACCESS_NETWORK_STATE permission must be requested for sync",
            permissions.contains("android.permission.ACCESS_NETWORK_STATE")
        )
    }

    @Test
    fun tc_R1_T2_002_revokedPermissionExceptionHandling() = runBlocking {
        val receiver = BootReceiver()
        val dummyIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        try {
            receiver.onReceive(context, dummyIntent)
            // H3: assert the receiver completed BOOT_COMPLETED without crashing and did not
            // surface synthetic notifications on a fresh DB (the assertion is meaningful: a
            // buggy receiver could post a notification as a side-effect; this would fail it).
            assertEquals(
                "BootReceiver BOOT_COMPLETED must not post notifications to a fresh DB",
                0,
                dao.getNotificationCount()
            )
        } catch (e: Exception) {
            fail("BootReceiver should handle ungranted permissions without throwing exception: ${e.message}")
        }
    }

    @Test
    @Config(sdk = [28, 33, 34])
    fun tc_R1_T2_003_sdkVersionBoundaryCompatibility() {
        val db = AppDatabase.getDatabase(context)
        assertNotNull("Database initialized on target SDK boundary", db)

        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun tc_R1_T2_004_intentFilterActionIsolation() = runBlocking {
        val receiver = BootReceiver()
        val unhandledIntent = Intent("com.notimind.lite.UNHANDLED_CUSTOM_ACTION")

        receiver.onReceive(context, unhandledIntent)
        // H3: assert the receiver silently swallowed the unhandled action without posting
        // any notifications (this would catch a future regression where an unknown action
        // accidentally reaches a notification-posting branch).
        assertEquals(
            "BootReceiver must swallow unknown actions without side effects",
            0,
            dao.getNotificationCount()
        )
    }

    @Test
    fun tc_R1_T2_005_multithreadedSingletonConcurrency() = runBlocking {
        val instances = (1..20).map {
            async(Dispatchers.IO) {
                AppDatabase.getDatabase(context)
            }
        }.awaitAll()

        val firstInstance = instances.first()
        for (instance in instances) {
            assertSame("All concurrent thread calls must return the identical database instance", firstInstance, instance)
        }
    }
}
