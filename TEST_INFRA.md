# Test Infrastructure & Specification: NotiMind Lite

## 1. Overview & Testing Philosophy

NotiMind Lite uses a zero-external-dependency, fully automated local testing architecture. The test suite runs completely within JVM unit testing infrastructure powered by **Robolectric 4.16.1**, **Room In-Memory Database**, **JUnit 4**, and **Kotlinx Coroutines Test**.

No real Android hardware devices, emulators, or cloud endpoints (Firebase, external servers) are required to execute the full suite.

## 2. Core Dependencies & Version Catalog

| Library / Tool | Version | Purpose |
|----------------|---------|---------|
| `junit:junit` | 4.13.2 | Core unit test runner & assertions |
| `org.robolectric:robolectric` | 4.16.1 | JVM-based Android framework & component simulation |
| `androidx.test:core` | 1.6.1 | Application context and lifecycle management |
| `androidx.test.ext:junit` | 1.3.0 | AndroidJUnit4 runner integration |
| `androidx.room:room-runtime` | 2.7.0 | SQLite In-Memory database for local persistence |
| `kotlinx-coroutines-test` | 1.10.2 | Coroutine test scope (`runTest`, `UnconfinedTestDispatcher`) |

## 3. Directory Layout & Test Suite Hierarchy

All test source files are organized under `app/src/test/java/com/notimind/lite/`:

```
app/src/test/java/com/notimind/lite/
├── base/
│   ├── BaseRobolectricTest.kt              # Base test class with context & database helper setup
│   └── RoomDatabaseRule.kt                 # JUnit Rule for managing In-Memory Room DB lifecycle
├── tier1_feature/
│   ├── ArchitectureSetupTest.kt            # TC-R1-T1-001 to TC-R1-T1-005 (R1 Core Architecture)
│   └── PersistenceSchemaTest.kt           # TC-R2-T1-001 to TC-R2-T1-006 (R2 Persistence Schema)
├── tier2_boundary/
│   ├── ArchitectureBoundaryTest.kt          # TC-R1-T2-001 to TC-R1-T2-005 (R1 Boundary & Permissions)
│   ├── PersistenceBoundaryTest.kt         # TC-R2-T2-001 to TC-R2-T2-007 (R2 Boundary & High Concurrency)
│   ├── ExportSanitizationBoundaryTest.kt    # JSON schema & CSV formula injection escaping
│   └── DynamicDebounceBoundaryTest.kt        # Smart 30s dynamic debounce validation
├── tier3_pairwise/
│   ├── NotificationServiceDbPairwiseTest.kt # Service + Room DB + Concurrent Ingestion
│   ├── BootReceiverDbPairwiseTest.kt        # BootReceiver + Room DB + Status Bar Deduplication
│   ├── ViewModelUiStatePairwiseTest.kt      # Room DB + Compose UI State Flow Recomposition
│   └── SettingsClearLogPairwiseTest.kt       # Settings Clear Log + Database Purge & Multi-Screen Sync
└── tier4_realworld/
    ├── EndToEndAppLifecycleTest.kt          # Full E2E Application Lifecycle Scenario
    ├── HighLoadBurstLifecycleTest.kt         # 10,000-event stress/stability & sudden reboot recovery
    └── PermissionRevocationLifecycleTest.kt  # Permission revocation & recovery lifecycle
```

## 4. Test Execution Commands

```bash
# Execute complete test suite (Tiers 1 - 4)
./gradlew test

# Execute debug unit tests
./gradlew testDebugUnitTest

# Execute specific tier
./gradlew testDebugUnitTest --tests "com.notimind.lite.tier1_feature.*"
./gradlew testDebugUnitTest --tests "com.notimind.lite.tier2_boundary.*"
./gradlew testDebugUnitTest --tests "com.notimind.lite.tier3_pairwise.*"
./gradlew testDebugUnitTest --tests "com.notimind.lite.tier4_realworld.*"
```

## 5. Summary of Test Case Index

| Test ID | Tier | Subject Area | Location |
|---------|------|--------------|----------|
| `TC-R1-T1-001` | Tier 1 | Zero AI/Firebase Gradle Audit | `tier1_feature/ArchitectureSetupTest.kt` |
| `TC-R1-T1-002` | Tier 1 | AndroidManifest Declarations | `tier1_feature/ArchitectureSetupTest.kt` |
| `TC-R1-T1-003` | Tier 1 | Database Singleton Init | `tier1_feature/ArchitectureSetupTest.kt` |
| `TC-R1-T1-004` | Tier 1 | Compose Setup & Navigation | `tier1_feature/ArchitectureSetupTest.kt` |
| `TC-R1-T1-005` | Tier 1 | Room DAO Proxy Configuration | `tier1_feature/ArchitectureSetupTest.kt` |
| `TC-R2-T1-001` | Tier 1 | Entity Fields & Default Values | `tier1_feature/PersistenceSchemaTest.kt` |
| `TC-R2-T1-002` | Tier 1 | DAO Insert & Row ID Auto-Generation | `tier1_feature/PersistenceSchemaTest.kt` |
| `TC-R2-T1-003` | Tier 1 | DAO Query Active List & Flow | `tier1_feature/PersistenceSchemaTest.kt` |
| `TC-R2-T1-004` | Tier 1 | DAO Query All History Log | `tier1_feature/PersistenceSchemaTest.kt` |
| `TC-R2-T1-005` | Tier 1 | DAO Mark Dismissed | `tier1_feature/PersistenceSchemaTest.kt` |
| `TC-R2-T1-006` | Tier 1 | DAO Clear All Wiping | `tier1_feature/PersistenceSchemaTest.kt` |
| `TC-R1-T2-001` | Tier 2 | Absence of Internet Permission | `tier2_boundary/ArchitectureBoundaryTest.kt` |
| `TC-R1-T2-002` | Tier 2 | Revoked Permission Exception Handling | `tier2_boundary/ArchitectureBoundaryTest.kt` |
| `TC-R1-T2-003` | Tier 2 | SDK Version Boundary Compatibility | `tier2_boundary/ArchitectureBoundaryTest.kt` |
| `TC-R1-T2-004` | Tier 2 | Intent Filter Action Isolation | `tier2_boundary/ArchitectureBoundaryTest.kt` |
| `TC-R1-T2-005` | Tier 2 | Multithreaded Singleton Concurrency | `tier2_boundary/ArchitectureBoundaryTest.kt` |
| `TC-R2-T2-001` | Tier 2 | Empty & Whitespace String Persistence | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-R2-T2-002` | Tier 2 | 100K Character Stress Payload | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-R2-T2-003` | Tier 2 | Emojis, Unicode, RTL & SQL Injection | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-R2-T2-004` | Tier 2 | Duplicate Key OnConflict Strategy | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-R2-T2-005` | Tier 2 | Optional Parameter Default Fallbacks | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-R2-T2-006` | Tier 2 | Timestamp Boundaries & Sorting | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-R2-T2-007` | Tier 2 | High-Concurrency DAO Operations | `tier2_boundary/PersistenceBoundaryTest.kt` |
| `TC-EXP-001` | Tier 2 | Export JSON Schema & Escaping | `tier2_boundary/ExportSanitizationBoundaryTest.kt` |
| `TC-EXP-002` | Tier 2 | Export CSV Formula Injection Escaping | `tier2_boundary/ExportSanitizationBoundaryTest.kt` |
| `TC-EXP-003` | Tier 2 | Export File Sharing & Cache Cleanup | `tier2_boundary/ExportSanitizationBoundaryTest.kt` |
| `TC-DEB-001` | Tier 2 | 30s Dynamic Debounce Validation | `tier2_boundary/DynamicDebounceBoundaryTest.kt` |
| `TC-T3-001` | Tier 3 | Room DB + Service Integration | `tier3_pairwise/NotificationServiceDbPairwiseTest.kt` |
| `TC-T3-002` | Tier 3 | BootReceiver Active Status Bar Deduplication | `tier3_pairwise/BootReceiverDbPairwiseTest.kt` |
| `TC-T3-003` | Tier 3 | ViewModel/Flow + Room DB + Compose UI | `tier3_pairwise/ViewModelUiStatePairwiseTest.kt` |
| `TC-T3-004` | Tier 3 | Settings Clear Log + Room DB Sync | `tier3_pairwise/SettingsClearLogPairwiseTest.kt` |
| `TC-T3-005` | Tier 3 | Concurrent Ingestion During Boot | `tier3_pairwise/NotificationServiceDbPairwiseTest.kt` |
| `TC-T4-001` | Tier 4 | Full End-to-End Application Lifecycle | `tier4_realworld/EndToEndAppLifecycleTest.kt` |
| `TC-T4-002` | Tier 4 | 10,000-Event Stress/Stability Handling | `tier4_realworld/HighLoadBurstLifecycleTest.kt` |
| `TC-T4-003` | Tier 4 | Permission Revocation & Recovery | `tier4_realworld/PermissionRevocationLifecycleTest.kt` |
