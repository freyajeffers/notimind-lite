package com.jeffers.notimindlite.tier1feature

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.ui.MainActivity
import com.jeffers.notimindlite.ui.Screen
import com.jeffers.notimindlite.ui.bottomTabNavigationOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainNavigationStateTest {
    @Test
    fun bottomTabNavigationOptionsSaveAndRestoreBackStackStateAtActiveRoot() {
        val options = bottomTabNavigationOptions()

        assertEquals(Screen.Active.route, options.popUpToRoute)
        assertTrue(options.shouldPopUpToSaveState())
        assertTrue(options.shouldRestoreState())
        assertTrue(options.shouldLaunchSingleTop())
    }

    @Test
    fun mainActivityDoesNotOverrideSystemOrientationPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, MainActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(component, 0)

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activityInfo.screenOrientation)
    }
}
