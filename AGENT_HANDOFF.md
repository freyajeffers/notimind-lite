# 🤖 Agent Handoff: NotiMind Lite

This document serves as the authoritative state-transfer for an AI agent taking over development, testing, and maintenance of the NotiMind Lite Android project.

## 🎯 Project Mission
**NotiMind Lite** is a privacy-focused "notification insurance" application. It captures, logs, and recovers system notifications, allowing users to retrieve dismissed messages via advanced semantic search and E2EE cloud synchronization.

## 🛠️ Technical Stack
- **OS Target**: Android 15 (API Level 36) | Min SDK: 26.
- **UI Layer**: Jetpack Compose with Material Design 3 (Dynamic Theming).
- **Persistence**: Room SQLite Database with FTS4 (Keyword Search) and Vector Embeddings (Semantic Search).
- **Cloud**: Firebase Auth (Google Sign-In) and Firestore for user-scoped bidirectional sync.
- **Background**: `NotificationListenerService` (Capture) and `WorkManager` (Sync).
- **Recovery**: Direct Boot support for pre-unlock accessibility.

## 🚦 Current State (as of August 2026)

### ✅ System Health
- **Build Status**: Stable. `./gradlew assembleDebug` is passing.
- **Git State**: Clean on `master`.
- **Verification**: All high-level features in `ideal_commits.txt` and `summary.md` have been verified on a physical device.
- **Runtime Performance**: LruCache implementation in `AppIconCache` eliminates scrolling jank in notification lists.

### 📜 Session History & Context
This project has undergone a rigorous "re-verification and hardening" phase. The most recent session focused on:
- **Reintegrating Ingestion Filters**: Restoring the comprehensive filter pipeline in `NotificationLoggerService` to eliminate system noise, blank notifications, and group summary clutter.
- **Boot & Startup Logic**: Hardening the `AppInitializer` $\rightarrow$ `NotiMindApp` sequence and registering the `SnoozeReminderReceiver` in the manifest to ensure reliable reminder triggers.
- **CI/CD Alignment**: Reverting accidental modifications to `.github/workflows/ci.yml` and `CI_CD.md` to ensure alignment with original project specifications.
- **UI/UX Audit**: Verifying the Material 3 implementation, specifically the collapsed/expanded states of notification cards and the "Reason" badge logic.

### 🧩 Deep-Dive: Key Implementation Details

#### 1. Notification Ingestion Pipeline (`NotificationLoggerService.kt`)
- **Rich Extraction**: Captures `bigText`, `subText`, `inboxLinesJson`, `actionLabels`, and `appIconUri`.
- **Ingestion Filters**: 
    - **Blacklist**: Drops notifications from known noise-generators (e.g., `com.android.shell`, Google Search scrapers).
    - **System Clutter**: Filters out USB debugging, charging status, and service-level toasts from `android` or `com.android.systemui`.
    - **Noise Suppression**: Drops group summary notifications (e.g., "5 more messages") and blank content notifications.
- **Dynamic Debounce**: Implements a 30-second window using `recentLogs` and `recentContents` maps to prevent duplicate database writes from rapid notification state oscillations.
- **Icon Persistence**: Captures app icons as PNGs in `cacheDir/app_icons` to ensure visual consistency even if the app is uninstalled.

#### 2. Search & Discovery (`HybridSearchEngine.kt`)
- **Hybrid Architecture**: Combines SQLite FTS4 (token-based) with on-device 128-dimensional dense vector embeddings.
- **Semantic Search**: Uses `VectorEmbeddingHelper` to calculate cosine similarity for intent-based discovery.
- **RRF Ranking**: Uses Reciprocal Rank Fusion (RRF) to merge results from both keyword and semantic paths into a single, balanced relevance list.
- **Candidate Set Optimization**: To prevent OOM and latency, the vector search is performed on a candidate set consisting of the union of FTS results and the most recent 1,000 notifications.
- **Domain Clustering**: `DynamicClusterManager` infers notification categories (Finance, Social, etc.) based on package names and content patterns.

#### 3. Cloud Sync & Security (`FirestoreSyncRepository.kt`)
- **E2EE Architecture**: Implements a "Zero-Knowledge" sync. All sensitive fields are encrypted client-side using AES-GCM 256-bit encryption via `SyncEncryptionHelper`.
- **Key Management**: Uses device-bound encryption keys; plaintext PII never leaves the device.
- **Sync Logic**: Orchestrated via `SyncWorker` (WorkManager) for efficient background bidirectional synchronization.

#### 4. Recovery & Storage (`AppDatabase.kt`, `BootReceiver.kt`)
- **Database Schema**: Current version **v17**.
- **Migration Strategy**: Strictly non-destructive. All schema evolutions use `ALTER TABLE` to prevent data loss.
- **Dual-Partition Storage**:
    - **DE (Device Encrypted)**: Used for `notimind_de.db` to allow capture during Direct Boot.
    - **CE (Credential Encrypted)**: Used for `notimind_ce.db` for main user data.
    - **Migration**: `UnlockReceiver` triggers a raw DB merge and FTS rebuild when the user unlocks the device.

#### 5. App Lifecycle & Memory (`NotiMindApp.kt`, `AppInitializer.kt`)
- **Startup**: `AppInitializer` coordinates thread-safe, ordered initialization of Firebase and core singletons.
- **Memory Management**: Implements `ComponentCallbacks2` to evict `AppIconCache` and `VectorEmbeddingHelper` caches during `onTrimMemory` and `onLowMemory` events.

#### 6. Utility Toolset
- **Secure Backups**: `EncryptedBackupManager` handles AES-GCM encrypted database backups with a remote notary signature via `BackupNotaryClient` to ensure authorization.
- **Data Portability**: `DatabaseExporter` supports CSV and JSON exports with specialized CSV field sanitization to prevent CSV injection attacks.
- **Intent Dispatch**: `NotificationLauncher` provides a secure wrapper for launching external app intents and action intents from notification cards.
- **Snooze Reminders**: `SnoozeReminderScheduler` uses `AlarmManager.setExactAndAllowWhileIdle` to trigger reminder notifications for snoozed items.

## ⚠️ Critical Guardrails (Non-Negotiable)
1. **Zero PII Leakage**: Never sync plaintext notification titles or content to Firestore. All cloud data must be encrypted client-side.
2. **Non-Destructive DB Updates**: Never use `DROP TABLE` or clear app data to upgrade the schema. Use Room migrations (`ALTER TABLE`) exclusively.
3. **Direct Boot Awareness**: Components accessing the database before user unlock must be marked `android:directBootAware="true"`.
4. **Memory Safety**: `AppIconCache` must be cleared during `onTrimMemory` and `onLowMemory` to prevent OOM crashes.
5. **Namespace Integrity**: Do not modify the package name `com.jeffers.notimindlite`.

## 🔍 Truth Sources (Artifacts)
The following files contain the definitive specifications:
- `summary.md`: Master Technical Implementation Guide.
- `ideal_commits.txt`: The logical sequence of project milestones.
- `ideal_changelog.md`: Semantic versioning and feature history.
- `AGENTS.md`: Agent operating procedures.

## 🧪 Verification Suite
To verify the system, execute the following flows:
1. **Capture Flow**: Post a notification $\rightarrow$ Verify `NotificationLoggerService` logs it $\rightarrow$ Verify it appears in `ActiveNotificationsScreen`.
2. **Recovery Flow**: Reboot device $\rightarrow$ Verify `BootReceiver` restores notifications $\rightarrow$ Verify no duplicates.
3. **Search Flow**: Enter a semantic query in `LogHistoryScreen` $\rightarrow$ Verify results are ranked via Hybrid Search.
4. **Sync Flow**: Sign in with Google $\rightarrow$ Trigger `SyncWorker` $\rightarrow$ Verify encrypted data reaches Firestore.
5. **Memory Flow**: Trigger `onTrimMemory` via ADB $\rightarrow$ Verify `AppIconCache` is cleared.

## 🚀 Next Recommended Steps
- [ ] **Automated Testing**: Expand `NotificationLoggerServiceTest` and add Robolectric tests for the `HybridSearchEngine`.
- [ ] **UI Polish**: Finalize Material 3 accessibility contrast for low-vision users.
- [ ] **Edge Case Testing**: Verify behavior during rapid-fire notification bursts (debounce stress test).
- [ ] **CI/CD Hardening**: Integrate automated UI tests via Firebase Test Lab.
