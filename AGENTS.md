# 🤖 AGENTS.md: NotiMind Lite Implementation Agent

Authoritative operating protocol for AI agents working on **NotiMind Lite**.
A backup of the v1 file is preserved at `AGENTS.md.bak.v1`.

## 🎯 Project Mission
Privacy-focused Android app that captures, logs, and recovers system notifications.
It provides a high-utility "notification insurance" policy: dismissed notifications are
recoverable via FTS4 keyword search, vector semantic search, hybrid RRF ranking,
and bidirectional Firestore sync.

## 🏗️ Technical Architecture
- **Language / SDK**: Kotlin, JDK 17 toolchain, `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`
- **UI Layer**: Jetpack Compose + Material 3 (dynamic theming); no XML layouts under `ui/`
- **Persistence**: Room SQLite (DB v18, 17 migrations), FTS4 virtual table for keyword search,
  BLOB column for vector embeddings (semantic search). NO Hilt/Dagger — constructor injection only.
- **Background**:
  - `NotificationLoggerService` (NotificationListenerService) for real-time capture
  - WorkManager `SyncWorker` for Firestore bidirectional sync
  - `BootReceiver` / `UnlockReceiver` for Direct Boot recovery
- **Cloud**: Firebase Auth via `CredentialManager` + `GoogleId` (no FirebaseUI),
  Firestore for user-scoped (`userId`) bidirectional sync.
- **Reliability**: Direct Boot (DE storage) for pre-unlock recovery, two-database
  pattern (`notimind_de.db` and `notimind_lite_database`).
- **DI**: NONE. Constructors + `AppInitializer` (singleton object, `AtomicBoolean` guard).

## 🧱 Package Map (Production Source)
Production code lives **exclusively** under `com.jeffers.notimindlite.*`. Tests live
under `com.notimind.lite.*` — see Test Stack below.

```
com.jeffers.notimindlite
├── NotiMindApp.kt               Application + ComponentCallbacks2 (cache trim)
├── data
│   ├── auth/                    AuthManager (CredentialManager + FirebaseAuth)
│   ├── local/                   Room: AppDatabase, NotificationDao/AppDao/BackupDao,
│   │                              entities (NotificationEntity, AppEntity,
│   │                              NotificationFtsEntity, BackupRecord), Migrations 1–17,
│   │                              MIGRATION_17_18 registered, Converters
│   ├── repository/              Domain repositories (search, sync wrappers)
│   └── sync/                    FirestoreSyncRepository, SyncWorker (WorkManager)
├── receiver
│   ├── BootReceiver.kt          directBootAware=true (LOCKED_BOOT_COMPLETED + ...)
│   ├── UnlockReceiver.kt        directBootAware=true (USER_UNLOCKED)
│   └── SnoozeReminderReceiver.kt
├── service
│   ├── NotificationLoggerService.kt  directBootAware=true, package|id|tag key derivation
│   └── RestoredNotificationManager.kt
├── ui                           Jetpack Compose screens (NO ViewModels — uses remember +
│                                 StateFlow collected in Composables), navigation,
│                                 theme, components, dialogs
└── util
    ├── AppInitializer.kt        Firebase init, guarded by AtomicBoolean + try/catch
    ├── AppIconCache.kt          LruCache; cleared in onTrimMemory
    ├── VectorEmbeddingHelper.kt Semantic vectors; cleared on low memory
    ├── HybridSearchEngine.kt    FTS4 + vector + RRF
    ├── ReciprocalRankFusion.kt  RRF scoring
    ├── DynamicClusterManager.kt Domain inference (Finance, Social, ...)
    ├── EncryptedBackupManager.kt AES-GCM + HMAC signed backups
    ├── DatabaseExporter.kt      CSV export + encrypted DB export
    ├── NotificationLauncher.kt  Safe external-intent dispatch
    ├── ActionableEntityExtractor.kt / ActionableChips.kt
    ├── AuditLogger.kt           Detects app data clear; persists signed backup log
    └── BackupKeyDialog.kt       Confirmation dialog shown before secret-key-bearing export
```

### Namespace Integrity (DO NOT BREAK)
- `applicationId = "com.jeffers.notimindlite"`
- `namespace = "com.jeffers.notimindlite"`
- `versionCode = 1`, `versionName = "1.0-lite"` — set in `app/build.gradle.kts`
  (the root `version.properties` is currently a sibling snapshot, not an auto-wired source —
  change both together or neither)
- `android.permission.QUERY_ALL_PACKAGES` is declared (with `tools:ignore="QueryAllPackagesPermission"`)
  — Play Store policy may require replacing it with `<queries>` blocks; do not remove without
  explicit user direction.

## 🛠️ Authoritative Reference Materials
Located at `/home/freya/antigravity/NotiMind-Lite/chat-extract/` (paths shown un-prefixed):

| File | Role |
|---|---|
| `summary.md` | "How" — literal diffs and phased requirements |
| `ideal_commits.txt` | "When" — definitive atomic commit order |
| `ideal_changelog.md` | Release milestones (mirror of root `ideal_changelog.md`) |

Project-root companions: `INSTRUCTIONS.md` (phase roadmap), `ARCHITECTURE.md`,
`MASTER_TECHNICAL_SPECIFICATION.md`, `DESIGN_DECISIONS.md`, `MODULE_API.md`,
`COMPLIANCE.md`, `PRIVACY_POLICY.md`, `TELEMETRY_SPEC.md`, `QUALITY_GATES.md`,
`TEST_INFRA.md`, `TROUBLESHOOTING.md`, `CI_CD.md`, `INSTALLATION.md`,
`DEVELOPER_GUIDE.md`, `USER_GUIDE.md`, `AGENT_HANDOFF.md`, `PROJECT.md`,
`TEST_READY.md`, `localization-ready-report.md`, `FUTURE_ROADMAP.md`.

When `chat-extract/` (source-of-truth) disagrees with the on-disk code (current truth),
ASK the user which wins before applying.

## 🚦 Operating Principles
1. **Linearity**: Strict dependency graph — Build → Data → Service → UI → Sync → Perf.
   Never skip a phase or a commit from `ideal_commits.txt`.
2. **Atomic Application**: One commit per change. Do not bundle.
3. **Additive Preference**: Prefer adding code over removing it. Latent dependencies
   in this codebase are not always obvious.
4. **Destructive Change Guard**: If a commit's deletions exceed its additions, STOP
   and request explicit user permission before applying (memory-enforced).
5. **Namespace Integrity**: Do not modify `applicationId`, `namespace`, or
   `versionName` without explicit user direction.
6. **Direct Boot Awareness**: Every component that touches the database before user
   unlock must set `android:directBootAware="true"` in `AndroidManifest.xml`.
   Currently set on: `NotificationLoggerService`, `BootReceiver`, `UnlockReceiver`.
   New components touching the DB pre-unlock MUST join this list.
7. **No Hilt / No Dagger**: Constructors only. Adding DI is a structural change that
   requires explicit user approval.
7a. **No ViewModels**: UI state is held in Composables via `remember` +
    `StateFlow.collectAsStateWithLifecycle` directly from repositories. Do NOT add
    `ViewModel` classes; `lifecycle-viewmodel-compose` is on the classpath but unused
    in the production tree.
8. **No Placeholder Data**: All implementations must use real, runtime-derived values.
   Hard-coded example IDs, names, or tokens are rejected by code review.
9. **Firebase Headless Safety**: `AppInitializer.initialize` wraps
   `FirebaseApp.initializeApp(context)` with `FirebaseApp.getApps(context).isEmpty()`
   guard and a top-level try/catch. Any new Firebase call site MUST follow the same
   pattern, or Robolectric/instrumented tests crash.
10. **LruCache Hygiene**: `AppIconCache.clearCache()` is called in `NotiMindApp.onTrimMemory`
    at TRIM_MEMORY_UI_HIDDEN / RUNNING_LOW / RUNNING_CRITICAL / COMPLETE,
    and in `onLowMemory()`. `VectorEmbeddingHelper.clearCache()` joins at the running-low
    tier and above. Any new in-memory cache MUST be wired into this trim ladder or
    it will OOM on low-end devices.
11. **Notification Key Contract**: Post and remove paths MUST derive `key` identically.
    Current contract: `sbn.key ?: "${sbn.packageName}|${sbn.id}|${sbn.postTime}"` (see
    `NotificationLoggerService` and `handleNotificationRemoved`). Drift between post
    and remove key derivation causes rows to share `key = "0"` / `null` and breaks
    dismissal matching.
12. **Room Migrations Only — Never Drop**: Schema changes go through
    `Migration(from, to)` objects registered in `AppDatabase.getCeInstance`. Do NOT
    use `fallbackToDestructiveMigration` or `.fallbackToDestructiveMigrationOnDowngrade`
    on user data paths.
13. **Compose Clipboard**: Always use `AnnotatedString` with
    `LocalClipboardManager.setText` — passing a `String` causes type mismatch warnings.
14. **Encrypted Exports**: Always show `BackupKeyDialog` (or equivalent confirmation)
    so the user has archived the secret key BEFORE the file is generated.

## 🧪 Test Stack

### Frameworks (pinned in `app/build.gradle.kts`)
- JUnit 4 (`testImplementation(libs.junit)`)
- MockK (`testImplementation(libs.mockk)`)
- Robolectric (`testImplementation(libs.robolectric)`), `unitTests.isIncludeAndroidResources = true`
- `kotlinx-coroutines-test`
- `androidx.test:core` and `androidx.test.ext:junit`
- **NO JUnit 5 / Kotest / Spek** in this codebase
- Instrumented: `androidx.test:runner`, `androidx.compose.ui:ui-test-junit4`
- Compose UI tests live in `app/src/androidTest/`

### Test Package Tiers (Tiered Bottom-Up)
Tests live under `app/src/test/java/com/notimind/lite/...` — a deliberately different
root from production to keep test infrastructure namespaced.

| Tier | Package | Purpose |
|---|---|---|
| 0 (legacy) | `com.jeffers.notimindlite.*` | Older scattered tests co-located with prod package; new tests go to tiers 1–4 |
| 1 Feature | `com.notimind.lite.tier1_feature` | Unit tests for one component in isolation (DAO, screen, Worker, ViewModel) |
| 2 Boundary | `com.notimind.lite.tier2_boundary` | Two-component boundaries (security, sync, RRF, vector, snooze loop, debounce, GCM integrity, export sanitization, persistence, backup) |
| 3 Pairwise | `com.notimind.lite.tier3_pairwise` | Cross-feature integration (BootReceiver ↔ DB, NotificationLoggerService ↔ DB, Settings ↔ Clear Log, Sync ↔ Repository, ViewModel ↔ UI State) |
| 4 Real-World | `com.notimind.lite.tier4_realworld` | Chaos / load / lifecycle (CloudChaos, NotificationBurst, PermissionRevocationLifecycle, EndToEndAppLifecycle, HighLoadBurst) |

Shared base class: `com.notimind.lite.base.BaseRobolectricTest`
(`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`,
in-memory Room via `Room.inMemoryDatabaseBuilder().allowMainThreadQueries()`,
`PRAGMA foreign_keys = OFF`, calls `AppDatabase.setTestInstance` and `resetInstance`).

### Test Commands
```
./gradlew :app:testDebugUnitTest         # tiers 0–4
./gradlew :app:connectedDebugAndroidTest # androidTest/ (instrumented)
./gradlew :app:testCoverageReport       # Jacoco HTML+XML report
```
Test code MUST call `AppDatabase.resetInstance()` in `@After` to prevent singleton
leaks across tests (see `BaseRobolectricTest.teardown`).

### Known Quirks
- `BaseRobolectricTest` disables `PRAGMA foreign_keys` — tests that rely on FK
  enforcement must re-enable locally.
- `@Config(sdk = [33])` is hardcoded. Bumping means verifying every tier test still
  passes (some rely on specific SDK behaviors).
- Robolectric tests run with `allowMainThreadQueries();` — concurrency bugs in DAO
  code will not surface here; test those with instrumented tests or stress tests.

## 🛠️ Build & Verification

### Gate Commands (per task)
| Concern | Command |
|---|---|
| Build | `./gradlew :app:assembleDebug` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Coverage | `./gradlew :app:testCoverageReport` |
| Lint (fail on new) | `./gradlew :app:lintDebug` (uses `lint-baseline.xml`) |
| Detekt | `./gradlew :app:detekt` (uses `detekt-baseline.xml`) |
| Release / R8 | `./gradlew :app:assembleRelease` (then `apkanalyzer dex packages` to verify Direct Boot classes aren't stripped) |
| Room migration | `:app:testDebugUnitTest --tests "*MigrationTest*"` |

For this repo the agreed gates are `assembleDebug` + `testDebugUnitTest`.
Do NOT claim "done" without the gate command exiting 0 in the same turn.

### Build Properties (`gradle.properties`)
- `org.gradle.jvmargs=-Xmx6g -XX:+UseParallelGC`
- `org.gradle.parallel=true`, `caching=true`, `configuration-cache=true`
- `android.nonTransitiveRClass=true`, `kotlin.incremental=true`, `ksp.incremental=true`
- `kotlin.compiler.execution.strategy=daemon`
- `org.gradle.workers.max=8`

### Room Schemas (exportSchema enabled)
`@Database(... exportSchema = true)` and `ksp.arg("room.schemaLocation",
"$projectDir/schemas")` together emit `app/schemas/<fully-qualified-class-name>/<version>.json`
on every Room compile. Currently only `18.json` is committed. Adding historical
schemas (1.json .. 17.json) is a deliberate one-time effort — each requires checking
out the historical commit, running `./gradlew :app:assembleDebug`, copying the
emitted JSON, and returning to master. See `tier1_feature/MigrationTest` for the
scaffolded migration test that consumes these files via `MigrationTestHelper`.

### Detekt / Lint / ProGuard
- `app/detekt-baseline.xml`, `app/lint-baseline.xml` exist — treat them as
  "known-acceptable noise", not a permission to add more.
- `app/proguard-rules.pro` is checked in. R8 is enabled for release (`isMinifyEnabled = true`,
  `isShrinkResources = true`). Always re-test after touching `proguard-rules.pro`.

## 📲 Runtime / On-Device Verification
After installing on a device, use this checklist before declaring "works on hardware."

1. **Confirm listener permission was granted:**
   `adb shell cmd notification allow_listener com.jeffers.notimindlite/com.jeffers.notimindlite.service.NotificationLoggerService`
2. **Service logs:**
   `adb logcat -d | grep NotificationLoggerSrv`
   Look for `onNotificationPosted`, `Inserted:`, `onListenerConnected`.
3. **Pull the Room DB for inspection (WAL-aware):**
   ```
   adb shell "run-as com.jeffers.notimindlite cat /data/data/com.jeffers.notimindlite/databases/notimind_lite_database"      > notimind.db
   adb shell "run-as com.jeffers.notimindlite cat /data/data/com.jeffers.notimindlite/databases/notimind_lite_database-wal" > notimind_wal.db
   sqlite3 notimind.db "SELECT count(*), isDismissed FROM notifications GROUP BY isDismissed;"
   ```
   Room uses WAL mode. Recent rows may live in the `-wal` file until a checkpoint.
   Force-stopping the app usually triggers one.
4. **Empty History tab diagnosis:** History shows only `isDismissed = 1`. The header
   `Log (X/Y)` shows X dismissed out of Y total. If `Log (0/Y)` appears, switch to
   Active to confirm Y > 0.
5. **Key-collision symptom:** many rows sharing `key = "0"` or `null` indicates
   post/remove key derivation drifted. Re-verify the package|id|tag fallback.
6. **Screenshot capture:**
   `adb shell "screencap -p /sdcard/screen.png" && adb pull /sdcard/screen.png`

## 🏁 Definition of Done (per task)
A task is **done** when ALL of the following are satisfied in the same turn:
1. The literal code from `summary.md` (or the user-supplied diff) is applied.
2. The associated `ideal_commits.txt` entry is implemented (or documented as future work).
3. `./gradlew :app:assembleDebug` exited 0.
4. `./gradlew :app:testDebugUnitTest` exited 0.
5. If a new Room migration was added: a migration test exists and passes.
6. If a new `directBootAware` manifest entry was added: receiver/service manifest
   entry was added AND a `pm lock`-then-restart smoke test was performed (or
   flagged as untested).
7. The atomic git commit is registered with the message from `ideal_commits.txt`.

If any gate fails, the task is NOT done — report the blocker honestly.

## ⚠️ Common Pitfalls
- **Skipping a phase** → cascading compile / DI / lifecycle failures.
- **Forgetting `directBootAware="true"`** on a pre-unlock DB-touching component
  → silent failure on reboot until first unlock.
- **Duplicate Firebase init** without the `getApps(context).isEmpty()` guard
  → crash in headless tests; only `AppInitializer` should call `initializeApp`.
- **Forgetting `AppIconCache.clearCache()` in a new cache class** → OOM on low-end
  devices after a few hours of backgrounded use.
- **Drift between post/remove notification key derivation** → UI deduplication
  breaks and dismissals don't match.
- **`fallbackToDestructiveMigration` on the user DB** → user data loss; never use.
- **Hard-coded sample IDs / names / tokens** → blocked at code review.
- **Schema downgrade** (bumping `@Database(version = N)` without a matching
  `Migration(N, N+1)`) → app crashes on every existing install.
- **`String` passed to `LocalClipboardManager.setText`** → Compose type mismatch warning.

## 📚 Reference Index
- Project root: `/home/freya/antigravity/NotiMind-Lite/`
- Build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `gradle/wrapper/`, `app/build.gradle.kts`
- Manifest: `app/src/main/AndroidManifest.xml`
- Source-of-truth docs: `summary.md`, `ideal_commits.txt`, `ideal_changelog.md`,
  `INSTRUCTIONS.md` (in `chat-extract/` and at the project root)
- Skill: `notimind-lite-coding` (loads via `skill_view`) — covers procedure, pitfalls,
  adb verification recipe, key-collision fix.

## 🤝 Working With Hermes on This Repo
- Load the `notimind-lite-coding` skill at the start of each session
  (or ask Hermes to load it).
- Reference commits by ID from `ideal_commits.txt` whenever possible.
- Paste real error output (logcat, Gradle, JUnit) — never paraphrased.
- For DB changes, ask which wins when `summary.md` and live code disagree.
- For destructive changes (deletions > additions), expect Hermes to halt and
  request explicit permission — this is enforced by memory.
- For multi-step work, prefer atomic commits even when bundling feels faster.
