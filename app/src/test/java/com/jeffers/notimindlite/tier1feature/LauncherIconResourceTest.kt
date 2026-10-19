package com.jeffers.notimindlite.tier1feature

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.base.BaseRobolectricTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LauncherIconResourceTest : BaseRobolectricTest() {
    @Before
    override fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @After
    override fun teardown() {
        // No in-memory database used in resource validation test
    }

    @Test
    fun manifestLauncherIcons_areRegisteredAndResolvable() {
        val appInfo = context.applicationInfo
        assertEquals(R.mipmap.ic_launcher, appInfo.icon)

        val launcherDrawable = context.getDrawable(R.mipmap.ic_launcher)
        val roundLauncherDrawable = context.getDrawable(R.mipmap.ic_launcher_round)
        assertNotNull("Primary launcher icon must resolve", launcherDrawable)
        assertNotNull("Round launcher icon must resolve", roundLauncherDrawable)
        assertTrue(
            "Launcher icon must resolve to AdaptiveIconDrawable or VectorDrawable on API 33",
            launcherDrawable is AdaptiveIconDrawable || launcherDrawable is VectorDrawable
        )
        assertTrue(
            "Round launcher icon must resolve to AdaptiveIconDrawable or VectorDrawable on API 33",
            roundLauncherDrawable is AdaptiveIconDrawable || roundLauncherDrawable is VectorDrawable
        )
    }

    @Test
    fun adaptiveAndSplashVectorDrawables_inflateWithValidBounds() {
        val drawableIds = listOf(
            R.drawable.ic_launcher_background,
            R.drawable.ic_launcher_foreground,
            R.drawable.ic_launcher_monochrome,
            R.drawable.ic_launcher_vector
        )

        for (resId in drawableIds) {
            val drawable = context.getDrawable(resId)
            assertNotNull("Drawable resource $resId must inflate cleanly", drawable)
            assertTrue(
                "Drawable resource $resId must have positive intrinsic width",
                (drawable?.intrinsicWidth ?: 0) > 0
            )
            assertTrue(
                "Drawable resource $resId must have positive intrinsic height",
                (drawable?.intrinsicHeight ?: 0) > 0
            )
        }
    }
}
