# NotiMind Lite: Troubleshooting Guide

This guide provides systemic diagnostics and resolution paths for common failures encountered during the development and operation of NotiMind Lite.

## 1. Common Error Messages & Resolution

### Security & Attestation
| Error / Exception | Cause | Resolution |
| :--- | :--- | :--- |
| `Play Integrity: DEVICE_INTEGRITY_FAILED` | Device is rooted, bootloader is unlocked, or emulator is used. | Ensure the device passes basic integrity checks. In debug mode, use a physical device with a locked bootloader. |
| `GeneralSecurityException` (during decryption) | AES-GCM authentication tag mismatch. Indicates ciphertext corruption or wrong key. | Verify that the `IV` and `Auth Tag` were not truncated during file transfer. Ensure the `SecretKey` used for decryption matches the encryption key. |
| `IntegrityTokenException` | Failure to communicate with Google Play Services. | Update Google Play Services on the device. Verify that the `applicationId` in `build.gradle` matches the Firebase project configuration. |

### Persistence & Data
| Error / Exception | Cause | Resolution |
| :--- | :--- | :--- |
| `IllegalStateException: Room cannot verify the data integrity` | Database schema mismatch after a version increment without a migration. | Implement a `Migration` object in `DatabaseMigrator` or use `fallbackToDestructiveMigration()` for development builds. |
| `SQLiteFullException` | Device storage is exhausted. | Implement a cleanup policy in `NotificationDao` to prune records older than $X$ days. |

---

## 2. Logcat Diagnostics

To trace the systemic flow of data, use the following filter tags in Android Studio Logcat.

### Capture Pipeline
- **`NotificationLoggerService`**: Trace the interception of raw OS notifications and the hand-off to the persistence layer.
- **`AppDatabase`**: Monitor SQLite transaction timings and potential locking issues.

### Intelligence & Search
- **`HybridSearchEngine`**: Trace the dual-pass search process.
- **`VectorEmbeddingHelper`**: Debug vector generation and cosine similarity calculations.
- **`ReciprocalRankFusion`**: Monitor the merging of FTS and Vector result sets.

### Security & Notary Handshake
- **`BackupNotaryClient`**: Trace the `TOKEN` $\rightarrow$ `SIGNATURE` handshake.
- **`EncryptedBackupManager`**: Log encryption/decryption lifecycle and Tink primitive initialization.

---

## 3. Environmental Issues

### Keystore Mismatches
If the app crashes on startup or fails to authenticate with Firebase/Play Integrity:
1. Verify that the SHA-256 fingerprint of your current signing key (Debug or Release) is registered in the **Firebase Console $\rightarrow$ Project Settings**.
2. Ensure that `signingConfigs` in `build.gradle.kts` points to the correct `.jks` file.

### Missing Google Services Configuration
The app requires `google-services.json` for Firebase and Play Integrity functionality.
- **Symptom**: `FileNotFoundException` or `FirebaseApp` initialization error.
- **Fix**: Download the latest `google-services.json` from the Firebase Console and place it in the `/app/` directory.

### Permission Failures
If notifications are not being captured:
- Ensure the user has manually granted **Notification Access** in `Settings $\rightarrow$ Special App Access $\rightarrow$ Notification Access`.
- Verify that `android:directBootAware="true"` is set in the `AndroidManifest.xml` for the `NotificationLoggerService`.
