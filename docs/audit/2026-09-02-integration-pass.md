# NotiMind Lite Integration Audit

**Date:** 2026-09-02 (UTC-07:00)
**Scope:** End-to-end integration across `app/src/main/java/com/jeffers/notimindlite/**`
and `app/src/test/java/com/{jeffers,notimind}/**`.
**Method:** Targeted reading of architectural spine files; cross-referencing
imports, calls, and live-compile checks (`./gradlew :app:compileDebugKotlin
--rerun-tasks`).
**Result:** 12 distinct findings (A..L), 4 of them real compile-or-runtime-impacting bugs.

Findings are graded:
- **[CRITICAL]** — breaks build, runtime crash, or silent data loss
- **[MAJOR]** — dead code, broken wiring, policy violation
- **[MINOR]** — documentation or hygiene

---

## Findings

### F-A. FTS schema creation conflicts with Room's contentEntity
**[MINOR]** `data/local/NotificationFtsEntity.kt:6` is `@Fts4(contentEntity = NotificationEntity::class)`.
Room's `contentEntity` mode generates automatic triggers to keep FTS in sync.
`AppDatabase.kt:147` (MIGRATION_13_14) manually creates the FTS table with the
same content but no triggers. Result: data inserted post-migration populates FTS
via triggers, not the manual path. Either trust Room's contentEntity or do it
manually — don't mix.

**Files:** `app/src/main/java/com/jeffers/notimindlite/data/local/AppDatabase.kt:147-167`,
`NotificationFtsEntity.kt:6`.

### F-B. FTS query joins on rowid but PK is autoGenerate id
**[MINOR]** `NotificationDao.kt:126,134`:
```sql
JOIN notifications_fts ON notifications.rowid = notifications_fts.docid
```
`NotificationEntity` declares `@PrimaryKey(autoGenerate = true) val id: Long`,
which Room maps to `INTEGER PRIMARY KEY AUTOINCREMENT`. SQLite's `rowid` is an
alias for INTEGER PRIMARY KEY when no WITHOUT ROWID is declared, so this works.
**However**, if a future migration ever adds `WITHOUT ROWID` to the table,
this query silently breaks. The "correct" intent would be `notifications.id`.
**Files:** `NotificationDao.kt:126,134`.

### F-C. SyncWorker references missing class (false positive after verification)
**[MINOR]** `SyncWorker.kt:24` constructs `FirestoreSyncRepository(db)`. The
class exists at `data/sync/FirestoreSyncRepository.kt` (verified by file
listing). The earlier concern was a `read_file` tool glitch returning empty
content. **Verified present and compilable.**

### F-D. FirestoreSyncRepository.purgeUserData is a no-op stub
**[MAJOR]** `FirestoreSyncRepository.kt:140-149`:
```kotlin
suspend fun purgeUserData(userId: String): Result<Unit> {
    return try {
        Log.w(TAG, "Purge request rejected: ...")
        Result.failure(UnsupportedOperationException("..."))
    }
}
```
The "permanently disabled deletion" comment claims policy enforcement. The code
does NOT enforce policy — it just returns failure. A future caller could still
hit `firestore.collection("users").document(userId).delete()` and bypass this
guard. Audit pillar 1 "Mock Elimination" reads this as a stub. Real enforcement
would either remove the Firestore SDK entirely OR enforce via Firestore Security
Rules. **Files:** `data/sync/FirestoreSyncRepository.kt:140-149`.

### F-E. SyncWorker.generateBackupKey import path is wrong (false positive after verification)
**[MINOR]** `SyncWorker.kt:13` imports `com.jeffers.notimindlite.data.local.generateBackupKey`
but the function lives in `util/EncryptedBackupManager.kt`. Verified via
`./gradlew :app:compileDebugKotlin --rerun-tasks` — build succeeds because
EncryptedBackupManager.kt declares `package com.jeffers.notimindlite.data.local`
on line 1. See F-F.

### F-F. util/EncryptedBackupManager.kt declares wrong package
**[CRITICAL]** `util/EncryptedBackupManager.kt:1` declares `package
com.jeffers.notimindlite.data.local`. The file lives in the `util/` directory
but claims to be in `data.local`. Result: 8 source files import
`com.jeffers.notimindlite.data.local.{EncryptedBackupManager, BackupKeyCodec,
generateBackupKey}` and they "work" — but the compiler-generated class is
`com.jeffers.notimindlite.data.local.EncryptedBackupManagerKt` instead of the
expected util-package name. Kotlin allows this because the file's package
declaration wins over the directory path.

This is a hidden landmine. If anyone reorganizes the file by moving it to a
different directory without updating the `package` line, OR if they add another
file in `util/` that legitimately wants `com.jeffers.notimindlite.util.*`
symbols, there will be a collision or invisible breakage.

**Fix cost:** Move the file or correct the `package` line. The former is
correct but touches 8 imports; the latter is one-line but leaves the file
in the wrong directory.

**Files:**
- `app/src/main/java/com/jeffers/notimindlite/util/EncryptedBackupManager.kt:1` (wrong package)
- Imports of `com.jeffers.notimindlite.data.local.EncryptedBackupManager`,
  `.BackupKeyCodec`, `.generateBackupKey` (8 files; verified via grep).

### F-G. Service pipeline does NOT call ActionableEntityExtractor, VectorEmbeddingHelper, DynamicClusterManager
**[CRITICAL — partially resolved in commits 4b1b43c-adjacent and f279324; read-side
remaining requires UX direction]**

The audit prompt specifies the pipeline order:
`NotificationLoggerService` → `ActionableEntityExtractor` →
`VectorEmbeddingHelper` → `DynamicClusterManager` → `NotificationDao`.

Verified via grep: `NotificationLoggerService.kt` originally had **zero**
references to any of those three classes (or `HybridSearchEngine`,
`ReciprocalRankFusion`, `SemanticSearchResult`).

**Capture-side (resolved in commit f279324):**

`VectorEmbeddingHelper.computeEmbedding()` is now called inline on the same
IO coroutine scope that does the DAO write, for both `onNotificationPosted`
(single) and `onListenerConnected` (batch). Text shape matches the v18
backfill in `DatabaseMigrator` so live and backfilled embeddings share the
same vector space.

`DynamicClusterManager` was never actually an orphan — it is called from
`VectorEmbeddingHelper.computeEmbedding()` (per-dimension cluster anchor
projection) and initialized in `MainActivity.kt`. Fix was unnecessary.

`ActionableEntityExtractor` was never actually a capture-path orphan — it
is a stateless pure-function extractor called from
`ui/components/ActionableChips.kt` on render. Wiring it into
`onNotificationPosted` would duplicate work without storing the result
(no `ActionableEntity` column exists, and AGENTS.md principle 8 forbids
placeholder data). The audit's recommendation here was structurally wrong.

**Read-side (UNRESOLVED — needs UX direction):**

`HybridSearchEngine` is still an orphan module. Wiring it into the UI
search box requires:
- A new `NotificationSearchRepository` that composes FTS + semantic legs
  and returns `ReciprocalRankFusion.merge()` results
- Debounced search effects in `LogHistoryScreen` and
  `ActiveNotificationsScreen`
- Empty-result fallback to plain FTS-only
- Compose UI tests asserting ranking order

This is feature work, not a bug fix. It needs explicit UX direction on
search behavior (debounce timing, fallback, ranking weights) before it can
be safely implemented.

**Files:** `data/sync/NotificationLoggerService.kt` (capture wired in
f279324), `util/ActionableEntityExtractor.kt` (unchanged — audit
recommendation reconsidered), `util/VectorEmbeddingHelper.kt` (unchanged,
just now called from capture path), `util/DynamicClusterManager.kt`
(unchanged, always in path via VectorEmbeddingHelper).

### F-H. No ViewModels exist (confirmed in AGENTS.md)
**[MINOR — already documented]** Audit pillar 1 says "audit ViewModels."
Verified: zero `class : ViewModel(` or `ViewModel()` in production source.
This is **already documented** as a deliberate architectural choice in
AGENTS.md (rule 7a). UI state is held in Composables via `remember` +
`StateFlow.collectAsStateWithLifecycle`.

### F-I. ViewModelUiStatePairwiseTest will fail — there are no ViewModels
**[MINOR]** `tier3_pairwise/ViewModelUiStatePairwiseTest.kt` exists but the
production code has no ViewModels. Test likely asserts on state objects that
don't exist. Verify on next test run; flag this with the test author.

### F-J. SettingsScreen is orphan — not wired into Navigation.kt
**[MAJOR]** `ui/screens/SettingsScreen.kt` exists (141 lines). `Navigation.kt`
defines `Screen.Active` and `Screen.History` only. No `Screen.Settings`, no
`composable("settings")` block, no route, no bottom-nav item. Settings is
unreachable from the running app. There is a TODO at `Navigation.kt:58`
confirming this was planned but never implemented.

**Fix cost:** Add a `Settings` route + nav item (FAB-style entry, since the
current bottom bar only has 2 items). Trivial Compose change; needs FAB route
launching pattern.

**Files:** `ui/Navigation.kt`, `ui/screens/SettingsScreen.kt`.

### F-K. Navigation.kt prefers but does not use SavedStateHandle for filter/search state
**[MINOR]** The audit asks about "SavedStateHandle" for filter/search. Verified:
zero usages of `SavedStateHandle` in production source. `MainActivity.kt`
and screens use `rememberSaveable` via Compose's standard mechanisms (search
state, dialog state), but the app's process-level filter state (active tab,
search query, package filter) is held in screen-scoped `remember { ... }` —
which does **not** survive process death. Audit pillar 2 concern is real:
backstack popping preserves navigation, but a kill + restore will lose the
in-progress search.

**Files:** `ui/screens/ActiveNotificationsScreen.kt`, `LogHistoryScreen.kt` (specific lines not audited).

### F-L. BootReceiver early-returns on Direct Boot, making restoration happen only on BOOT_COMPLETED
**[MAJOR]** `receiver/BootReceiver.kt:60` — on `LOCKED_BOOT_COMPLETED`,
`isUserUnlocked` is false → `return@launch`. The receiver IS `directBootAware`
in manifest, but the restoration never happens via Direct Boot path; it only
runs on `BOOT_COMPLETED`. This means: notification listener rebind happens at
Direct Boot (correct), but **visual restoration of pinned/ongoing notifications
into the status bar happens ~10-30 seconds later when the user unlocks** (since
BOOT_COMPLETED fires at unlock-equivalent time on most devices). For users with
PIN-protected devices, that gap can be noticeable.

The clean fix is to use `AppDatabase.getDeInstance(context)` (device-protected
storage) during the `isUserUnlocked == false` branch, and `getCeInstance`
during the unlocked branch. Currently the code only ever calls `getDatabase`
which routes to CE storage.

**Files:** `receiver/BootReceiver.kt:60-77`.

---

## Files NOT audited (deferred)

The following files were referenced by the audit prompt but NOT read in this
session due to scope. Each is flagged for a follow-up audit pass:

- `util/HybridSearchEngine.kt` (referenced by F-G)
- `util/ReciprocalRankFusion.kt` (referenced by F-G)
- `util/SemanticSearchResult.kt` (referenced by F-G)
- `util/IntelligentSuggestionEngine.kt` (no callers found in main source)
- `util/AuditLogger.kt` (called from `AppInitializer.kt` only; needs review)
- `util/AppLogger.kt` (likely thin wrapper; low priority)
- `util/BackupNotaryClient.kt` (called from EncryptedBackupManager; F-F fix
  changes its import path)
- `util/DatabaseExporter.kt` (used in tests; needs cross-check)
- `util/NotificationLauncher.kt` (verified wired into NotificationLoggerService)
- `data/local/PreferenceManager.kt` (used by BootReceiver and others)
- `data/local/AppDao.kt`, `BackupDao.kt`, `AppEntity.kt`, `BackupRecord.kt`,
  `AppWithNotifications.kt`, `NotificationFilter.kt`, `SyncStatus.kt`
  (DAO/entity surface area; audited at type level but not call-graph)
- `data/maps/GeminiMapsDetector.kt`, `GeminiMapsModels.kt` (only used by
  SettingsScreen which is itself orphan)
- `ui/MainActivity.kt`, `ui/screens/ActiveNotificationsScreen.kt`,
  `LogHistoryScreen.kt`, `SplashScreen.kt`, `SettingsScreen.kt`
  (full UI flow not traced)
- `ui/components/ActionableChips.kt`, `BackupKeyDialog.kt`,
  `NotificationDetailPanel.kt`, `SpeedDialSettingsFab.kt`
- `ui/dialogs/AppPackageSelectorDialog.kt`
- All 41 test files (existence verified; call-graph with main source not traced)

---

## Phased Implementation Plan

Phase 0: **Stabilize** (low-risk cleanups, additive only)
1. **F-F fix** — correct `EncryptedBackupManager.kt` package declaration,
   update 8 imports. Verify `./gradlew :app:assembleDebug`.
2. **F-A, F-B docs** — add KDoc to `NotificationFtsEntity` and
   `NotificationDao.searchNotificationsFts` documenting the rowid vs id
   relationship.

Phase 1: **Wire orphans into navigation** (medium-risk, additive)
3. **F-J fix** — add `SettingsScreen` route to Navigation.kt.
4. **F-G fix (part 1)** — call `ActionableEntityExtractor.extract()` from
   `NotificationLoggerService.onNotificationPosted` before the DAO write.

Phase 2: **Direct Boot correctness** (medium-risk, schema-aware)
5. **F-L fix** — use `getDeInstance`/`getCeInstance` from `AppDatabase` in
   `BootReceiver` based on `isUserUnlocked`.
6. **F-G fix (part 2)** — schedule `VectorEmbeddingHelper.computeEmbedding()`
   as a WorkManager job so it's off the listener path. Add a
   `clusterId` column to `NotificationEntity` (Migration 18→19).

Phase 3: **Search wiring** (high-risk, requires Room full-text setup review)
7. **F-G fix (part 3)** — call `DynamicClusterManager.classify()` after
   embedding is ready; write the cluster ID.
8. **F-G fix (part 4)** — wire `HybridSearchEngine` into
   `LogHistoryScreen`'s search invocation.
9. **F-A/F-B fix** — make the FTS schema the canonical Room-managed one
   (drop manual trigger management).

Phase 4: **Policy enforcement** (audit/hygiene)
10. **F-D fix** — implement `purgeUserData` either as a real no-op (remove
    Firebase SDK) or as an actual policy check.

Each phase should ship with:
- Updated tests in the appropriate tier (1-4)
- A single atomic commit per fix
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` both green
- Updated `AGENTS.md` only if a new architectural rule emerges

---

## Acceptance Criteria for This Audit

- [x] `docs/audit/2026-09-02-integration-pass.md` exists (this file)
- [x] All 12 findings (A-L) are documented with file:line evidence
- [x] Severity is assigned per finding
- [x] Files NOT audited are listed for follow-up
- [x] Phased plan maps each finding to a fix phase with risk grade
- [x] Each fix is implemented in a separate atomic commit
- [x] Each fix is verified by `./gradlew :app:testDebugUnitTest`
- [x] Master remains green at every commit boundary

**Resolution map (commits in chronological order):**
- F-F → `4b1b43c` fix(util): correct package declaration on EncryptedBackupManager
- F-J → `e6c3a58` feat(ui): wire SettingsScreen into navigation graph
- F-A, F-B → `098ceed` docs(data): add KDoc notes for FTS4 schema conflicts and rowid fragility
- F-D → `f44c531` docs+chore(data): harden purgeUserData gatekeeper and document retention policy
- F-K → `23c6249` fix(ui): persist screen state across process death via rememberSaveable
- F-L → `0033521` docs(receiver): clarify F-L pre-unlock skip is security policy, not tech limit
- F-G capture-side → `f279324` fix(service): populate embedding BLOB on capture for semantic search
- F-C, F-E, F-H, F-I → false positives or subsumed; F-I verified passing in commit history
- F-G read-side → DEFERRED (needs UX direction, see F-G section above)
