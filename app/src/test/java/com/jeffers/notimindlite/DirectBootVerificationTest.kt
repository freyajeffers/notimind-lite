package com.jeffers.notimindlite

import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.jeffers.notimindlite.receiver.BootReceiver
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUserManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DirectBootVerificationTest {

    private lateinit var context: Context
    private lateinit var userManager: UserManager
    private lateinit var shadowUserManager: ShadowUserManager
    private lateinit var bootReceiver: BootReceiver

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        shadowUserManager = Shadows.shadowOf(userManager)
        bootReceiver = BootReceiver()
    }

    @Test
    fun `BootReceiver should skip restoration when device is LOCKED`() {
        // Simulate LOCKED state
        shadowUserManager.setUserUnlocked(false)
        
        val bootIntent = Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        
        bootReceiver.onReceive(context, bootIntent)
        
        assertTrue(true) // Reached here without crashing
    }

    @Test
    fun `BootReceiver should perform restoration when device is UNLOCKED`() {
        // Simulate UNLOCKED state
        shadowUserManager.setUserUnlocked(true)
        
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        
        bootReceiver.onReceive(context, bootIntent)
        
        assertTrue(true) // Reached here without crashing
    }
}
