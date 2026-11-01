package com.jeffers.notimindlite.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestoredNotificationStateTest {
    @Test
    fun recordsAndClearsNotificationIdWithoutLosingCapturedPresentationMetadata() {
        val captured = JSONObject()
            .put("titleBig", "Delivery update")
            .put("messages", JSONArray().put(JSONObject().put("text", "Package nearby")))
            .toString()

        val restored = RestoredNotificationState.withNotificationId(captured, 1_042)

        assertEquals(1_042, RestoredNotificationState.notificationId(restored))
        assertEquals("Delivery update", JSONObject(restored).getString("titleBig"))
        assertEquals(
            "Package nearby",
            JSONObject(restored).getJSONArray("messages").getJSONObject(0).getString("text")
        )

        val cleared = RestoredNotificationState.withoutNotificationId(restored)

        assertFalse(JSONObject(cleared!!).has(RestoredNotificationState.NOTIFICATION_ID_FIELD))
        assertEquals("Delivery update", JSONObject(cleared).getString("titleBig"))
        assertNull(RestoredNotificationState.notificationId(cleared))
    }

    @Test
    fun queuedStateIsPersistentAndCanBeRemovedWithoutLosingCapturedMetadata() {
        val captured = "{\"titleBig\":\"Chat update\"}"
        val queued = RestoredNotificationState.withQueuedState(captured)

        assertTrue(RestoredNotificationState.isQueued(queued))
        assertNull(RestoredNotificationState.notificationId(queued))
        assertEquals("Chat update", JSONObject(queued).getString("titleBig"))

        val cleared = RestoredNotificationState.withoutRestoreState(queued)

        assertFalse(RestoredNotificationState.isQueued(cleared))
        assertEquals("Chat update", JSONObject(cleared!!).getString("titleBig"))
    }

    @Test
    fun malformedMetadataIsNotConsideredRestored() {
        assertNull(RestoredNotificationState.notificationId("not-json"))
        assertTrue(RestoredNotificationState.withNotificationId("not-json", 1_043).isNotBlank())
    }
}
