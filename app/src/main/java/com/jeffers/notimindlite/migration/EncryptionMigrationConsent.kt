package com.jeffers.notimindlite.migration

import android.content.Context

/** Persisted user decision controlling legacy plaintext database encryption migration. */
object EncryptionMigrationConsent {
    enum class Decision { UNKNOWN, ACCEPTED, DECLINED }

    private const val PREFS_NAME = "notimind_encryption_migration_consent"
    private const val DECISION_KEY = "decision"
    private const val UNKNOWN_VALUE = "unknown"
    private const val ACCEPTED_VALUE = "accepted"
    private const val DECLINED_VALUE = "declined"

    fun get(context: Context): Decision = when (
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(DECISION_KEY, UNKNOWN_VALUE)
    ) {
        ACCEPTED_VALUE -> Decision.ACCEPTED
        DECLINED_VALUE -> Decision.DECLINED
        else -> Decision.UNKNOWN
    }

    fun set(context: Context, decision: Decision) {
        require(decision != Decision.UNKNOWN) { "A persisted consent must be accepted or declined" }
        val value = when (decision) {
            Decision.ACCEPTED -> ACCEPTED_VALUE
            Decision.DECLINED -> DECLINED_VALUE
            Decision.UNKNOWN -> error("Unknown consent cannot be persisted")
        }
        check(
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(DECISION_KEY, value)
                .commit()
        ) { "Unable to persist encryption migration consent" }
    }
}
