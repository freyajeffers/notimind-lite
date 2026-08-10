package com.notimind.lite.tier3_pairwise

import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SettingsClearLogPairwiseTest : BaseRobolectricTest() {

    @Test
    fun tc_T3_004_settingsClearLogRoomDbStateSynchronization() = runTest {
        // Populate DB with 5 entities
        for (i in 1..5) {
            dao.insertNotification(createDummyEntity(key = "k_settings_$i", isDismissed = i % 2 == 0))
        }

        assertEquals(5, dao.getNotificationCount())
        assertFalse(dao.getAllNotifications().first().isEmpty())

        // Execute Clear Log operation
        dao.clearAll()

        assertEquals(0, dao.getNotificationCount())
        assertTrue("Active notifications flow must be empty", dao.getActiveNotificationsFlow().first().isEmpty())
        assertTrue("All notifications flow must be empty", dao.getAllNotifications().first().isEmpty())
    }
}
