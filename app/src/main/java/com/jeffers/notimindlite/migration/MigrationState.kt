package com.jeffers.notimindlite.migration

enum class MigrationState {
  NOT_REQUIRED,
  PREFLIGHT,
  COPYING,
  VERIFYING,
  CUTOVER_PENDING,
  COMPLETE,
  RETRYABLE_FAILURE,
  ROLLBACK_REQUIRED
}
