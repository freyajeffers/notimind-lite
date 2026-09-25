package com.jeffers.notimindlite.migration

import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationRunnerTest : BaseRobolectricTest() {

  @Test
  fun featureFlagOffReturnsNotRequired() = runTest {
    val state = MigrationRunner(context).runMigrationIfNeeded(featureFlag = false)
    assertEquals(MigrationState.NOT_REQUIRED, state)
  }

  @Test
  fun preflightReportsMissingPlaintextSourceWithoutWriting() {
    val preflight = MigrationRunner(context).preflight()
    assertEquals(false, preflight.plaintextExists)
    assertEquals(false, preflight.encryptedExists)
  }

  @Test
  fun featureFlagOnStartsPreflight() = runTest {
    val state = MigrationRunner(context).runMigrationIfNeeded(featureFlag = true)
    assertEquals(MigrationState.PREFLIGHT, state)
  }
}
