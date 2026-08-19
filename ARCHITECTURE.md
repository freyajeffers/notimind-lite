# NotiMind Lite: Technical Architecture Specification

## 1. Executive Summary
NotiMind Lite is a high-performance Android application designed for the systemic interception, semantic filtering, and archival of system notifications. The system transforms ephemeral notifications into a searchable, structured database, employing a hybrid search architecture (Full-Text Search + Vector Embeddings) and a "Zero-Trust" remote notarization system for secure backups.

---

## 2. System Architecture Overview
The application follows a layered architecture with a heavy emphasis on background reliability and data integrity.

### 2.1 High-Level Component Diagram
- **Capture Layer**: `NotificationListenerService` -> `NotificationLoggerService`
- **Persistence Layer**: Room DB (SQLite) -> `AppDatabase` -> `NotificationDao`
- **Intelligence Layer**: `HybridSearchEngine` -> `VectorEmbeddingHelper` -> `DynamicClusterManager`
- **Security Layer**: `EncryptedBackupManager` -> `BackupNotaryClient` -> Google Play Integrity API
- **UI Layer**: Jetpack Compose -> Material 3 -> `MainActivity` -> `Navigation`

---

## 3. Module Deep Dives

### 3.1 Capture & Ingestion (The Pipeline)
The app uses a specialized service to intercept notifications in real-time.

- **`NotificationLoggerService`**: A `NotificationListenerService` that intercepts `SNotificationListenerService.NotificationListenerEvent`.
- **Processing Flow**:
    1. **Intercept**: Capture `StatusBarNotification` (SBN).
    2. **Normalize**: Extract `packageName`, `title`, `text`, and `postTime`.
    3. **Enrich**: Use `AppEntity` to link the notification to a specific application.
    4. **Persist**: Atomic insertion into the `notifications` table.
- **Reliability**: Implements a smart debounce mechanism (30s) to prevent duplicate logging of the same notification during system updates.

### 3.2 Persistence Layer (The Vault)
The application uses a normalized SQLite schema managed via Room.

- **Schema Version 16**: Supports complex migrations.
- **Core Entities**:
    - `NotificationEntity`: The primary record of a notification.
    - `AppEntity`: Metadata about the source application (icon URI, package name).
    - `BackupRecord`: Audit log of encrypted backup exports.
- **FTS Integration**: Uses `NotificationFtsEntity` (FTS4) to provide near-instant full-text search across millions of records.
- **Direct Boot Support**: The database is configured for device-protected storage, allowing the `BootReceiver` and `UnlockReceiver` to restore state before the user unlocks the device.

### 3.3 The Intelligence Engine (Hybrid Search)
The "Brain" of the app combines traditional keyword matching with semantic vector space projection.

- **Vector Embeddings**: `VectorEmbeddingHelper` generates dense vectors for notification content. It uses a subword n-gram approach and L2 normalization to map text into a semantic space.
- **Semantic Clustering**: `DynamicClusterManager` extracts application categories from the `PackageManager` (e.g., `CATEGORY_APP_MESSAGING`) and maps them to 22 semantic domains.
- **Hybrid Search Logic**:
    - **FTS Pass**: Retrieves documents containing exact keywords.
    - **Vector Pass**: Retrieves documents with the highest cosine similarity to the query vector.
    - **Fusion**: Uses **Reciprocal Rank Fusion (RRF)** to merge and rank results from both passes, ensuring that "relevant" results appear regardless of whether they match keywords or intent.
- **Performance**: Employs an `LruCache` for embeddings to eliminate redundant computations during search.

### 3.4 Security & Backup Architecture (The Digital Notary)
The backup system implements a "Zero-Knowledge" client model.

- **Encryption**: Uses `AES-256-GCM` (Galois/Counter Mode) with a 128-bit authentication tag and a unique 12-byte IV per backup.
- **Key Management**: The `SecretKey` is generated locally and presented to the user via `BackupKeyDialog`. The server never sees the encryption key.
- **Digital Notary Flow**:
    1. **Hash**: App computes `SHA-256` of the encrypted backup file.
    2. **Attest**: App requests a nonce-based token from the **Google Play Integrity API**.
    3. **Notarize**: App sends `(FileHash, IntegrityToken)` to the Remote Notary Server.
    4. **Verify**: The server verifies the token (proving the app is genuine) and signs the hash using a hardened internal salt.
    5. **Store**: The resulting HMAC-SHA256 signature is stored in the `BackupRecord` for future verification.

### 3.5 UI & User Experience
The interface is a modern Material 3 implementation.

- **State Management**: Uses Compose `remember` and `mutableStateOf` for reactive UI updates.
- **Key Screens**:
    - `ActiveNotificationsScreen`: A real-time view of currently active system notifications.
    - `LogHistoryScreen`: A deep-archive view with advanced hybrid filtering and metadata panels.
- **Optimization**: `AppIconCache` uses an `LruCache` to store downsampled bitmaps, preventing OOM crashes and eliminating UI jank during fast scrolling.

---

## 4. Technical Stack
- **Language**: Kotlin 1.9+ / JVM 17
- **UI**: Jetpack Compose / Material 3
- **Database**: Room (SQLite) / FTS4
- **Concurrency**: Kotlin Coroutines / Flow / Dispatchers.IO
- **Security**: Tink / Google Play Integrity / AES-GCM
- **Target SDK**: 36 | **Min SDK**: 26

---

## 5. Data Flow: The Life of a Notification
`OS Notification` -> `NotificationLoggerService` -> `Normalization` -> `Room DB` -> `FTS Indexing` -> `HybridSearchEngine` -> `Material 3 UI`

---

## 6. Extensibility Guide
- **Adding a Search Domain**: Update the `DynamicClusterManager` vocabulary and mapping logic.
- **Adding a Backup Provider**: Implement a new `BackupNotaryClient` target while maintaining the `SHA-256` -> `Signature` contract.
- **Updating DB Schema**: Add a migration in `DatabaseMigrator` and increment the version in `AppDatabase`.
