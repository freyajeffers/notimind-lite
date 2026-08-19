# NotiMind Lite: Internal Module API Reference

This document defines the internal API contracts for the core utilities of NotiMind Lite. All implementations must strictly adhere to the threading and return-type specifications listed here.

---

## 1. HybridSearchEngine
The orchestrator for the dual-pass search architecture. It coordinates keyword (FTS) and semantic (Vector) searches and fuses the results.

### `search(query: String): Flow<List<NotificationEntity>>`
- **Description**: Executes a hybrid search pass and emits a sorted list of matching notifications.
- **Logic**: 
  1. Triggers `NotificationDao.searchNotificationsFts(query)`.
  2. Generates a query vector via `VectorEmbeddingHelper`.
  3. Computes cosine similarity against all stored notification vectors.
  4. Applies `ReciprocalRankFusion` to merge the two sorted lists.
- **Threading**: MUST be called on `Dispatchers.IO`.
- **Return**: A `StateFlow` containing the fused results.

---

## 2. EncryptedBackupManager
The cryptographic handler responsible for securing database exports.

### `encryptDatabase(dbFile: File): EncryptedBackup`
- **Parameters**: `dbFile` (The source SQLite file).
- **Description**: Generates a random 256-bit AES key and encrypts the database file using AES-GCM.
- **Logic**: 
  1. Generates a unique 12-byte IV.
  2. Uses Tink's `AesGcm` primitive to encrypt the stream.
  3. Appends the 16-byte authentication tag.
  4. Returns the ciphertext and the encrypted key.
- **Threading**: MUST be called on `Dispatchers.IO`.
- **Return**: `EncryptedBackup` data object (Ciphertext, IV, Tag).

### `decryptDatabase(backup: EncryptedBackup, key: SecretKey): File`
- **Parameters**: `backup` (The encrypted data), `key` (The decrypted AES key).
- **Description**: Reverses the encryption process and restores the SQLite file.
- **Threading**: MUST be called on `Dispatchers.IO`.
- **Return**: A temporary `File` containing the decrypted database.

---

## 3. BackupNotaryClient
The communication bridge between the application and the Remote Notary Server.

### `requestSignature(fileHash: String, nonce: String): Result<String>`
- **Parameters**: `fileHash` (SHA-256 of the encrypted backup), `nonce` (Server-provided salt).
- **Description**: Performs the attestation handshake to get a verifiable signature.
- **Logic**: 
  1. Calls `IntegrityManager.requestIntegrityToken()`.
  2. Sends the token, hash, and nonce to `POST /v1/notarize`.
  3. Validates the server response.
- **Threading**: MUST be called on `Dispatchers.IO`.
- **Return**: A `Result` containing the HMAC-SHA256 signature string.

---

## 4. NotificationDao
The primary data access object for the Room persistence layer.

### `searchNotificationsFts(query: String): List<NotificationEntity>`
- **Parameters**: `query` (Search string).
- **Description**: Performs a high-speed keyword search using the SQLite FTS4 virtual table.
- **Threading**: Room handles the execution on its internal thread pool; callers should wrap in `withContext(Dispatchers.IO)`.
- **Return**: List of notifications sorted by FTS relevance.

### `insertNotification(notification: NotificationEntity): Long`
- **Parameters**: `notification` (The entity to persist).
- **Description**: Inserts a captured notification and automatically synchronizes the corresponding entry in the `notifications_fts` table.
- **Threading**: MUST be called on `Dispatchers.IO`.
- **Return**: The row ID of the inserted record.
