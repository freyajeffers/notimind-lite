package com.jeffers.notimindlite.tier1feature

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.jeffers.notimindlite.util.BackupNotaryClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupNotaryIntegrityRequestDeviceTest {
    @Test
    fun requestUsesNonceAndReturnsTheIntegrityToken() = runBlocking {
        val manager = FakeIntegrityManager(FakeIntegrityTokenResponse("integrity-token"))

        val token = BackupNotaryClient.requestIntegrityToken(manager, "request-nonce")

        assertEquals("integrity-token", token)
        assertNotNull(manager.receivedRequest)
        assertEquals("request-nonce", manager.receivedRequest?.nonce())
    }

    private class FakeIntegrityManager(
        private val response: IntegrityTokenResponse
    ) : IntegrityManager {
        var receivedRequest: IntegrityTokenRequest? = null
            private set

        override fun requestIntegrityToken(request: IntegrityTokenRequest): Task<IntegrityTokenResponse> {
            receivedRequest = request
            return Tasks.forResult(response)
        }
    }

    private class FakeIntegrityTokenResponse(
        private val tokenValue: String
    ) : IntegrityTokenResponse() {
        override fun showDialog(activity: Activity, requestCode: Int): Task<Int> = Tasks.forResult(0)

        override fun token(): String = tokenValue
    }
}
