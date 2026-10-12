package com.notimind.lite.tier1feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.migration.EncryptionMigrationConsent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EncryptionMigrationConsentTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("notimind_encryption_migration_consent", 0)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun teardown() {
        context.getSharedPreferences("notimind_encryption_migration_consent", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun decisionPersistsAcrossReads() {
        assertEquals(EncryptionMigrationConsent.Decision.UNKNOWN, EncryptionMigrationConsent.get(context))

        EncryptionMigrationConsent.set(context, EncryptionMigrationConsent.Decision.ACCEPTED)
        assertEquals(EncryptionMigrationConsent.Decision.ACCEPTED, EncryptionMigrationConsent.get(context))

        EncryptionMigrationConsent.set(context, EncryptionMigrationConsent.Decision.DECLINED)
        assertEquals(EncryptionMigrationConsent.Decision.DECLINED, EncryptionMigrationConsent.get(context))
    }
}
