# Project: NotiMind Lite

## Architecture
NotiMind Lite is a high-performance Android application for systemic notification interception, semantic filtering, and secure archival. It employs a hybrid search architecture and a zero-trust remote notarization system.

**Package Structure:**
- `com.jeffers.notimindlite.data`: Persistence layer, including Room DB, DAOs, and Entities.
- `com.jeffers.notimindlite.data.auth`: Identity management via Firebase Auth and Google Sign-In.
- `com.jeffers.notimindlite.data.sync`: Cloud synchronization via Google Cloud Firestore.
- `com.jeffers.notimindlite.service`: Background services for notification capture and boot recovery.
- `com.jeffers.notimindlite.util`: Intelligence engine (Hybrid Search, RRF, Vectors), Security (AES-GCM, Notary), and System Utilities.
- `com.jeffers.notimindlite.ui`: Jetpack Compose UI, Material 3 screens, and Navigation.

## Core Implementation Milestones

| # | Name | Scope | Status |
|---|------|-------|--------|
| 1 | Project Foundation | Gradle setup, Compose/Room dependencies, AppInitializer | DONE |
| 2 | Identity & Cloud Sync | Firebase Auth, UserSession, FirestoreSyncRepository, SyncWorker | DONE |
| 3 | Notification Engine | NotificationLoggerService, Room Schema (v16), FTS4 Integration | DONE |
| 4 | Intelligence Layer | VectorEmbeddingHelper, DynamicClusterManager, HybridSearchEngine, RRF | DONE |
| 5 | Security & Notary | EncryptedBackupManager, BackupNotaryClient, Play Integrity API | DONE |
| 6 | UI/UX Implementation | LogHistoryScreen, ActiveNotificationsScreen, SpeedDial FAB, SplashScreen | DONE |
| 7 | Stability & Performance | AppIconCache, ComponentCallbacks2 (TrimMemory), Direct Boot Support | DONE |

## Interface Contracts

### Intelligence Layer
- `HybridSearchEngine.search(query: String): Flow<List<HybridSearchResult>>`
- `ReciprocalRankFusion.merge(ftsList, vectorList): List<Result>`
- `VectorEmbeddingHelper.embed(text: String): FloatArray`

### Data & Persistence
- `NotificationDao.searchNotificationsFts(query: String): List<NotificationEntity>`
- `AppDatabase.getNotificationDao(): NotificationDao`
- `EncryptedBackupManager.createBackup(): File`

### Security & Notary
- `BackupNotaryClient.signHash(hash: String, token: String): String`
- `Google Play Integrity API` $\rightarrow$ `IntegrityToken` $\rightarrow$ `Notary Server` $\rightarrow$ `HMAC-SHA256 Signature`

## Code Layout
```
app/src/main/java/com/jeffers/notimindlite/
├── NotiMindApp.kt
├── data/
│   ├── auth/
│   │   ├── AuthManager.kt
│   │   └── UserSession.kt
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── NotificationDao.kt
│   │   ├── NotificationEntity.kt
│   │   └── NotificationFtsEntity.kt
│   ├── sync/
│   │   ├── FirestoreSyncRepository.kt
│   │   └── SyncWorker.kt
│   └── maps/
│       └── GeminiMapsDetector.kt
├── service/
│   ├── NotificationLoggerService.kt
│   └── RestoredNotificationManager.kt
├── util/
│   ├── HybridSearchEngine.kt
│   ├── VectorEmbeddingHelper.kt
│   ├── ReciprocalRankFusion.kt
│   ├── EncryptedBackupManager.kt
│   └── BackupNotaryClient.kt
├── receiver/
│   ├── BootReceiver.kt
│   └── UnlockReceiver.kt
└── ui/
    ├── MainActivity.kt
    ├── Navigation.kt
    ├── screens/
    │   ├── LogHistoryScreen.kt
    │   └── ActiveNotificationsScreen.kt
    └── components/
        └── SpeedDialSettingsFab.kt
```
