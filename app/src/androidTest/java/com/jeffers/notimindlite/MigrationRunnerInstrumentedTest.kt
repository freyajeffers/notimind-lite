package com.jeffers.notimindlite

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.migration.MigrationRunner
import com.jeffers.notimindlite.migration.MigrationState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationRunnerInstrumentedTest {
  @Test
  fun featureFlagOffDoesNotTouchDatabase() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val state = kotlinx.coroutines.runBlocking {
      MigrationRunner(context).runMigrationIfNeeded(featureFlag = false)
    }
    assertEquals(MigrationState.NOT_REQUIRED, state)
  }
}
