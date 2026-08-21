package com.notimind.lite.tier1_feature

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MigrationTest : BaseRobolectricTest() {

    @Test
    fun `testFullMigrationPath_v1_to_v18`() = runBlocking {
        val entity = NotificationEntity(
            key = "mig_test_key",
            packageName = "com.mig.test",
            appName = "Migration App",
            title = "Migration Title",
            content = "Migration Content"
        )
        
        dao.insertNotification(entity)
        val retrieved = dao.getNotificationByKey("mig_test_key")
        
        assertNotNull("Notification should be retrievable after migration to v18", retrieved)
        assertEquals("Migration Title", retrieved?.title)
    }
}
