# Phase 2 — Cryptographic Hardening & Keystore Storage Security

## 1. Scope & Objectives

Phase 2 implements SQLCipher open-helper wiring for newly created Room DE/CE database instances, Keystore-wrapped passphrases, and AndroidKeyStore-backed encrypted preference storage. Portable user backups retain the application-specific AES-GCM/PBKDF2 envelope because AndroidX `EncryptedFile` is device-bound and cannot support cross-device restore.

## 2. Architectural Components & Responsibilities

| Component | Primary Responsibility | Security Architecture |

| --- | --- | --- |

| KeystoreKeyManager | Generates, stores, and manages hardware-backed AES-256 keys inside Android Keystore; handles passphrase wrapping and key rotation. | Never exports raw private key material; requires user authentication when hardware StrongBox is available. |

| EncryptedDatabaseFactory | Builds Room database instances backed by net.zetetic.database.sqlcipher.SupportFactory. | Injects decrypted passphrase byte array directly into SQLCipher native driver; zeroes array from memory immediately after opening. |

| DatabaseMigrationOrchestrator | Performs safe, transactional, one-time offline migration from plaintext SQLite to encrypted SQLCipher via sqlcipher_export(). | Cryptographically verifies row counts and schema integrity before shredding the old plaintext database file. |

| EncryptedPreferenceManager | Stores authentication tokens and PKCE material in versioned encrypted preferences. | AndroidX `EncryptedSharedPreferences` with an AES-256-GCM AndroidKeyStore `MasterKey`; values can be rewrapped without changing their logical names. |

## 3. Database PRAGMA Configuration & Storage Tuning

To ensure strict security and prevent database locking deadlocks under concurrent coroutine writes, SQLCipher must be initialized with the following PRAGMAs:

PRAGMA key = '...'; -- 256-bit passphrase provided via SupportFactoryPRAGMA cipher_page_size = 4096; -- Standard 4KB cryptographic page boundariesPRAGMA kdf_iter = 256000; -- PBKDF2 iteration count for robust key stretchingPRAGMA cipher_hmac_algorithm = HMAC_SHA512;PRAGMA cipher_default_kdf_algorithm = PBKDF2_HMAC_SHA512;PRAGMA journal_mode = WAL; -- Write-Ahead Logging for non-blocking concurrent readsPRAGMA synchronous = NORMAL; -- Resilient, high-performance flush mode for WALPRAGMA busy_timeout = 5000; -- 5000ms retry buffer preventing SQLITE_BUSY exceptionsPRAGMA foreign_keys = ON;

## 4. One-Time Plaintext Migration Protocol

Crucial Migration Safety: Existing user installations must not lose logged notifications during the upgrade to Phase 2. The DatabaseMigrationOrchestrator follows this atomic transition sequence:

- Inspect private filesystem for presence of legacy notimind_lite_database.

- If present, open legacy SQLite database in read-only mode.

- Create and initialize target encrypted database notimind_lite_encrypted.db using SQLCipher.

- Attach encrypted database to the plaintext connection and invoke sqlcipher_export('encrypted').

- Execute checksum and row-count verification across all tables.

- Upon successful validation, securely shred the legacy plaintext database and journal files (overwriting with zeroes before unlinking).

## 5. Security Invariants & Concurrency Guarantees

- Zero Memory Leaks of Key Material: Passphrase character/byte arrays must be explicitly cleared (filled with 0x00) after initializing SupportFactory.

- No Destructive Fallback: The dangerous .fallbackToDestructiveMigration() directive must be strictly removed from production builds. Schema migrations must be versioned explicitly.

- Single-Writer Actor Model: All database insert and update operations are serialized through coroutine channels or a dedicated Room writer dispatcher to eliminate lock contention on encrypted pages.

## 6. Key Rotation & Backup Guidelines

- `EncryptedPreferencesStore.rotate()` creates a new versioned AES-256-GCM `MasterKey`, copies authenticated values, commits the new store, and then removes the old store. Callers must rotate only while the process is in a stable authenticated state.
- Supabase PKCE verifiers and access/refresh tokens use this store. Legacy custom-encrypted `supabase_auth` values are read once, migrated, and deleted after successful re-encryption.
- SQLCipher database passphrases remain wrapped by AndroidKeyStore keys. Rotating a wrapper key must not change the database passphrase or the database file; otherwise the database becomes unrecoverable.
- Portable backups use a passphrase-derived PBKDF2-HMAC-SHA256 KEK and AES-GCM-wrapped DEK. Never rotate or discard the passphrase-derived wrapping parameters while backups may still need restoration.
- Device-bound KeyStore backups cannot be restored after uninstall or on another device. Use a passphrase-wrapped export for migration, and keep the passphrase outside the device backup and source tree.

### Status

The Phase 2 implementation is complete: SQLCipher uses Keystore-wrapped passphrases, plaintext database migration is transactional and verified before shredding, authentication secrets use versioned `EncryptedSharedPreferences`, StrongBox is attempted with an explicit TEE fallback, and rotation/portable-backup behavior is documented and tested. Device acceptance tests remain required on a physical Android target for release sign-off.

## 7. Acceptance Criteria & Verification Tests

- TC-CRYPTO-001 (At-Rest Inspection): Pull the database file via ADB on a device; executing sqlite3 against a SQLCipher database without the key must fail with an invalid-database error.

- TC-CRYPTO-002 (Keystore Binding): Verify that the encryption key is successfully generated inside the Android Keystore and is backed by hardware (StrongBox/TEE where supported).

- TC-CRYPTO-003 (Migration Integrity): Populate legacy database with 2,000 notifications; run migration; verify exactly 2,000 notifications reside in the encrypted database.
