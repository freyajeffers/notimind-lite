package com.notimind.lite.tier1_feature

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jeffers.notimindlite.data.local.AppDatabase
import com.notimind.lite.base.BaseRobolectricTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArchitectureSetupTest : BaseRobolectricTest() {

    @Test
    fun tc_R1_T1_001_zeroAiFirebaseDependencyAudit() {
        val rootDir = File("../../..")
        val buildGradleFile = File(rootDir, "app/build.gradle.kts")
        val versionsTomlFile = File(rootDir, "gradle/libs.versions.toml")

        val forbiddenKeywords = listOf("firebase", "google-ai", "tensorflow", "mlkit", "pytorch", "cloud-sync")

        if (buildGradleFile.exists()) {
            val content = buildGradleFile.readText().lowercase()
            for (keyword in forbiddenKeywords) {
                assertFalse("Forbidden dependency keyword '$keyword' found in app/build.gradle.kts", content.contains(keyword))
            }
        }

        if (versionsTomlFile.exists()) {
            val content = versionsTomlFile.readText().lowercase()
            for (keyword in forbiddenKeywords) {
                assertFalse("Forbidden dependency keyword '$keyword' found in libs.versions.toml", content.contains(keyword))
            }
        }
    }

    @Test
    fun tc_R1_T1_002_androidManifestComponentAndPermissionDeclarations() {
        val packageManager = context.packageManager
        val packageName = context.packageName

        val packageInfo = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES
        )

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertTrue("POST_NOTIFICATIONS permission must be declared", requestedPermissions.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue("RECEIVE_BOOT_COMPLETED permission must be declared", requestedPermissions.contains("android.permission.RECEIVE_BOOT_COMPLETED"))

        val serviceIntent = Intent("android.service.notification.NotificationListenerService").setPackage(packageName)
        val services = packageManager.queryIntentServices(serviceIntent, 0)
        assertFalse("NotificationLoggerService must be registered with listener action", services.isEmpty())

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(packageName)
        val receivers = packageManager.queryBroadcastReceivers(bootIntent, 0)
        assertFalse("BootReceiver must be registered for BOOT_COMPLETED", receivers.isEmpty())
    }

    @Test
    fun tc_R1_T1_003_applicationDatabaseContextInitializationSingleton() {
        val db1 = AppDatabase.getDatabase(context)
        val db2 = AppDatabase.getDatabase(context)

        assertNotNull("Database instance 1 should not be null", db1)
        assertNotNull("Database instance 2 should not be null", db2)
        assertSame("AppDatabase.getDatabase must return singleton reference", db1, db2)
    }

    @Test
    fun tc_R1_T1_004_jetpackComposeSetupAndNavigationIntegration() {
        val activityIntent = Intent(context, com.jeffers.notimindlite.ui.MainActivity::class.java)
        val resolveInfo = context.packageManager.resolveActivity(activityIntent, 0)

        assertNotNull("MainActivity should be resolved in manifest", resolveInfo)
    }

    @Test
    fun tc_R1_T1_005_roomDatabaseInstanceBuilderConfiguration() {
        assertTrue("Database should be open", database.isOpen)
        val daoInstance = database.notificationDao()
        assertNotNull("DAO getter should return non-null proxy", daoInstance)
    }
}
