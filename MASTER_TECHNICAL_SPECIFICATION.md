# NotiMind Lite: The Definitive Master Technical Specification

This document is the absolute source of truth for the NotiMind Lite project. It synthesizes the High-Level Architecture, the Technical Deep Dive, and the Master Engineering Manual into a single, exhaustive reference.

---

## PART I: ARCHITECTURAL FOUNDATIONS

### 1.1 Executive Summary
NotiMind Lite is a high-performance Android application designed for the systemic interception, semantic filtering, and archival of system notifications. The system transforms ephemeral notifications into a searchable, structured database, employing a hybrid search architecture (Full-Text Search + Vector Embeddings) and a "Zero-Trust" remote notarization system for secure backups.

### 1.2 System Invariants & Constraints
The system is governed by four non-negotiable engineering constraints:
1. **Zero-Knowledge Signing**: The application binary never possesses the signing keys. Trust is delegated to a remote Notary via hardware-backed attestation.
2. **Semantic Primacy**: Search is not limited to keywords; it is driven by a hybrid of exact evidence (FTS) and intent (Vector Space Projection).
3. **Zero-Jank UI**: No I/O, bitmap decoding, or vector computation occurs on the Main thread. All heavy operations are offloaded to `Dispatchers.IO`.
4. **System-Level Resilience**: The app remains functional through Direct Boot (DE storage) and responds dynamically to Android OS memory pressure.

### 1.3 High-Level Component Diagram
- **Capture Layer**: `NotificationListenerService` $\\rightarrow$ `NotificationLoggerService`
- **Persistence Layer**: Room DB (SQLite) $\\rightarrow$ `AppDatabase` $\\rightarrow$ `NotificationDao`
- **Intelligence Layer**: `HybridSearchEngine` $\\rightarrow$ `VectorEmbeddingHelper` $\\rightarrow$ `DynamicClusterManager`
- **Security Layer**: `EncryptedBackupManager` $\\rightarrow$ `BackupNotaryClient` $\\rightarrow$ Google Play Integrity API
- **Cloud Sync Layer**: `SyncWorker` $\\rightarrow$ `FirestoreSyncRepository` $\\rightarrow$ Google Cloud Firestore
- **Identity Layer**: `AuthManager` $\\rightarrow$ `UserSession` $\\rightarrow$ Firebase Auth
- **UI Layer**: Jetpack Compose $\\rightarrow$ Material 3 $\\rightarrow$ `MainActivity` $\\rightarrow$ `Navigation`

---

## PART II: ENGINEERING MANUAL (IMPLEMENTATION MAP)

### 2.1 Codebase Map (Class Directory)

#### Data Layer (`com.jeffers.notimindlite.data`)
- **`AppDatabase`**: Room database singleton. Manages SQLite connection and provides DAOs.
- **`NotificationDao`**: Primary data access point. Contains complex `@Query` logic for FTS and hybrid search.
- **`AppDao`**: Manages the `AppEntity` table, ensuring a 1:N relationship between apps and notifications.
- **`BackupDao`**: Handles the audit trail of backup exports and their corresponding signatures.
- **`NotificationEntity`**: The data model for a captured notification.
- **`NotificationFtsEntity`**: The virtual FTS4 table used for high-speed text indexing.

#### Service Layer (`com.jeffers.notimindlite.service`)
- **`NotificationLoggerService`**: The entry point. A `NotificationListenerService` that captures OS events.
- **`RestoredNotificationManager`**: Logic for re-injecting notifications into the system from a backup file.

#### Intelligence Layer (`com.jeffers.notimindlite.util`)
- **`HybridSearchEngine`**: The orchestrator. Coordinates the FTS and Vector passes and applies RRF.
- **`VectorEmbeddingHelper`**: The mathematician. Computes dense vectors and calculates cosine similarity.
- **`DynamicClusterManager`**: The context provider. Maps installed apps to semantic domains.
- **`ReciprocalRankFusion`**: The ranker. Implements the RRF algorithm to merge search results.

#### Security Layer (`com.jeffers.notimindlite.util`)
- **`EncryptedBackupManager`**: The cryptographer. Handles AES-GCM encryption and orchestrates the notarization flow.
- **`BackupNotaryClient`**: The communicator. Handles the Google Play Integrity handshake and Notary Server API calls.
- **`BackupKeyCodec`**: Utility for Base64 encoding/decoding of `SecretKey` objects.

#### UI Layer (`com.jeffers.notimindlite.ui`)
- **`MainActivity`**: Root activity. Handles permission requests and edge-to-edge layout.
- **`Navigation`**: Routing logic using Jetpack Compose Navigation.
- **`ActiveNotificationsScreen`**: Real-time view of current system notifications.
- **`LogHistoryScreen`**: Deep-archive view with hybrid search integration.
- **`AppIconCache`**: Memory-efficient bitmap manager using `LruCache`.

### 2.2 Precise Logic Sequences

#### The "Notarization" Sequence
1. **Initiate**: User clicks "Backup".
2. **Encrypt**: `EncryptedBackupManager` generates a random 256-bit key $\rightarrow$ Encrypts DB $\rightarrow$ Prepends IV $\rightarrow$ Appends GCM Tag.
3. **Hash**: `EncryptedBackupManager` computes $\text{SHA-256}(\text{File})$.
4. **Attest**: `BackupNotaryClient` calls `IntegrityManager.requestIntegrityToken()`.
5. **Request**: App sends `(Hash, Token, Nonce)` to the Remote Notary Server.
6. **Verify**: Server verifies the token $\rightarrow$ Checks APK hash $\rightarrow$ Checks Device Integrity.
7. **Sign**: Server computes $\text{Signature} = \text{HMAC-SHA256}(\text{InternalSalt}, \text{Hash})$.
8. **Finalize**: App receives the signature and saves it to the `BackupRecord` table.

#### The "Hybrid Search" Sequence
1. **Query**: User types "Bank transfer".
2. **FTS Pass**: `NotificationDao.searchNotificationsFts("Bank transfer")` $\rightarrow$ returns list $L_{FTS}$.
3. **Vector Pass**:
   - `VectorEmbeddingHelper.embed("Bank transfer")` $\rightarrow$ generates vector $\mathbf{q}$.
   - `HybridSearchEngine` calculates $\mathbf{q} \cdot \mathbf{v}_i$ for all records.
   - Returns sorted list $L_{Vector}$.
4. **Fusion**: `ReciprocalRankFusion.merge(L_{FTS}, L_{Vector})`.
   - $Score = \frac{1}{60 + rank_{FTS}} + \frac{1}{60 + rank_{Vector}}$.
5. **UI Update**: Results are emitted as a `StateFlow` to the Compose UI.

---

## PART III: TECHNICAL DEEP DIVE (THE MATH & CRYPTO)

### 3.1 Vector Space Theory & Embeddings
The app maps natural language into a $\mathbb{R}^n$ vector space.

- **Tokenization**: Text is split into subword n-grams to handle typos (e.g., "Searching" $\rightarrow$ `[sea, ear, arc, rch, chi, hin, ing]`).
- **L2 Normalization**: To ensure similarity is based on direction rather than magnitude, every vector $\mathbf{v}$ is normalized: $\mathbf{\hat{v}} = \frac{\mathbf{v}}{\|\mathbf{v}\|_2}$.
- **Similarity Metric**: Because vectors are L2-normalized, the **Cosine Similarity** is equivalent to the **Dot Product**: $\text{sim}(\mathbf{a}, \mathbf{b}) = \sum_{i=1}^{n} a_i b_i$.

### 3.2 Reciprocal Rank Fusion (RRF) Mathematics
To combine keyword and semantic lists, the app uses RRF:
$$Score(d) = \sum_{r \in R} \frac{1}{k + rank(r, d)}$$
- **$k$ (Constant)**: Set to $60$. This prevents a document ranked #1 in one list from overwhelmingly dominating if it is completely absent from the other.

### 3.3 Encryption Details (AES-256-GCM)
Encryption is handled via the **Tink** library.
- **Primitive**: `AES-GCM` (Galois/Counter Mode).
- **Storage Format**: `[12-byte random IV] + [Ciphertext] + [16-byte Auth Tag]`.
- **Authentication**: The 16-byte tag ensures that any modification to the ciphertext results in a `GeneralSecurityException` during decryption.

### 3.4 Remote Notary API Contract
**Request (`POST /v1/notarize`):**
```json
{
  "file_hash": "sha256_hex_string",
  "integrity_token": "google_play_integrity_jwt",
  "nonce": "random_server_generated_nonce",
  "timestamp": "iso8601_timestamp"
}
```
**Response (`200 OK`):**
```json
{
  "signature": "hmac_sha256_hex_string",
  "notary_id": "server_node_id",
  "expires_at": "iso8601_timestamp"
}
```

---

## PART IV: SYSTEM-LEVEL ENGINEERING

### 4.1 OS Integration & Lifecycle
- **Direct Boot**: The app uses `android:directBootAware="true"`.
    - **DE Storage**: `AppDatabase` is stored in Device Protected storage to enable basic functionality before the first unlock.
    - **CE Storage**: Encryption keys are stored in Credential Encrypted storage, accessed only after `ACTION_USER_UNLOCKED`.
- **Snooze Engine**: `SnoozeReminderScheduler` uses `AlarmManager.setExactAndAllowWhileIdle` to bypass Android Doze mode restrictions.

### 4.2 Performance Engineering
- **Bitmap Memory Management**: `AppIconCache` uses sampled decoding.
    - **Formula**: $\text{Cache Size} = \frac{\text{Available Heap}}{8}$.
    - **Sampling**: $\text{inSampleSize}$ is calculated to decode icons at $64 \times 64$ pixels, reducing memory footprint by up to $64\times$.
- **Memory Pressure Response**: Implements `ComponentCallbacks2`.
    - `TRIM_MEMORY_UI_HIDDEN` $\rightarrow$ Purge `AppIconCache`.
    - `TRIM_MEMORY_RUNNING_LOW` $\rightarrow$ Purge all internal caches and trigger `System.gc()`.

---

## PART V: FAILURE MODE & EFFECTS ANALYSIS (FMEA)

| Scenario | Mitigation Strategy | Result |
| :--- | :--- | :--- |
| **Notary Server Offline** | Local caching of last valid signature + exponential backoff. | Backup created; "Notarized" status pending. |
| **Rooted Device** | Play Integrity `deviceIntegrity` check fails. | Server refuses to sign; backup marked "Unverified". |
| **DB Corruption** | `DatabaseMigrator` $\rightarrow$ `fallbackToDestructiveMigration()`. | DB wiped and restored from latest verified backup. |
| **Main Thread I/O** | `StrictMode` enabled in debug $\rightarrow$ Move to `Dispatchers.IO`. | Prevents ANRs (App Not Responding). |
| **Snooze Alarm Missed** | `SnoozeReminderReceiver` checks time on boot. | Missed reminders fire immediately after unlock. |

---

## PART VI: OPERATIONAL GUIDE & EXTENSIONS

### 6.1 Debugging & Profiling
- **DB Audit**: Use Android Studio Database Inspector to verify `notifications_fts` synchronization.
- **Notary Trace**: Filter logs by `BackupNotaryClient` to trace the `TOKEN` $\rightarrow$ `SIGNATURE` handshake.
- **UI Profiling**: Use CPU Profiler to ensure `AppIconImage` does not trigger excessive recompositions.

### 6.2 Extension Guide
- **Updating Search**: Modify the $k$ constant in `ReciprocalRankFusion.kt` to shift the balance between semantic and exact results.
- **Schema Change**: Add column $\rightarrow$ Increment `AppDatabase` version $\rightarrow$ Define `Migration` in `DatabaseMigrator`.
- **New Notary**: Implement a new `BackupNotaryClient` target that conforms to the `signHash(hash, token): String` contract.
