package com.notimind.lite.tier3_pairwise

import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ViewModelUiStatePairwiseTest : BaseRobolectricTest() {

    @Test
    fun tc_T3_003_viewModelFlowStateAndRoomDbRecompositionSync() = runTest {
        // Initial state: empty DB
        var activeFlow = dao.getActiveNotificationsFlow().first()
        assertTrue("Active notifications flow should initially be empty", activeFlow.isEmpty())

        // Insert notification
        val entity = createDummyEntity(key = "k_flow_1", title = "Flow Title", content = "Flow Body")
        dao.insertNotification(entity)

        activeFlow = dao.getActiveNotificationsFlow().first()
        assertEquals(1, activeFlow.size)
        assertEquals("Flow Title", activeFlow[0].title)

        // Dismiss notification
        dao.markDismissed("k_flow_1")

        activeFlow = dao.getActiveNotificationsFlow().first()
        assertTrue("Active notifications flow should become empty after dismissal", activeFlow.isEmpty())

        val historyFlow = dao.getAllNotifications().first()
        assertEquals(1, historyFlow.size)
        assertTrue(historyFlow[0].isDismissed)
    }
}
