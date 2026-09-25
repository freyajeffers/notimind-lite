# Changelog

All notable changes to NotiMind Lite are documented here. This project adheres to [Conventional Commits](https://www.conventionalcommits.org/).

---

## [Unreleased] - Ingestion Pipeline Sanitization & Crypto Keystore

### 🚀 Features

- **feat(sanitization)**: Implement `PiiRedactionEngine` for deterministic pre-ingestion redaction of OTPs, Luhn-verified cards, phone numbers, emails, and currency values.
- **feat(sanitization)**: Implement `PackageFilterManager` and fail-closed `SanitizationPipeline` for package filtering and ingestion protection.
- **feat(prefs)**: Add PII Redaction toggle in `PreferenceManager` and `SettingsScreen`.
- **feat(crypto)**: Implement `CryptoUtils` with RFC 5869 HKDF-SHA256 key derivation and AES-256-GCM authenticated encryption.
- **feat(crypto)**: Implement `KeyManager` wrapper for AndroidX Security MasterKeys with JVM test fallback.
- **feat(crypto)**: Wire SQLCipher-backed Room open helpers for both Direct Boot databases, with per-database Keystore-wrapped passphrases.
- **feat(data)**: Advance the Room baseline to schema v19 with notification-group persistence and explicit `MIGRATION_18_19`.

### 🧪 Testing

- **test(crypto)**: Add unit tests for `CryptoUtils` and `KeyManager` covering roundtrip encryption, nonce uniqueness, AAD mismatch, tamper rejection, and RFC HKDF derivation.

---

## [0.6.0] - Performance & Stability

### 🚀 Features

- **feat(core)**: Implement `SnoozeReminderScheduler` and `SnoozeReminderReceiver` for custom in-app snooze timers.
- **feat(util)**: Implement `NotificationLauncher` for secure external intent dispatching.

### ⚡ Performance

- **perf(ui)**: Implement `AppIconCache` using `LruCache` to optimize notification list scrolling.
- **perf(mem)**: Integrate `ComponentCallbacks2` to handle OS memory trimming events and prevent OOM crashes.

### 🎨 UI/UX

- **chore**: Final polish of UI contrast and accessibility tokens for compliance.
- **style**: Update `styles.xml` and `MainActivity` for final visual consistency.

---

## [0.5.0] - Cloud Connectivity

### 🚀 Features

- **feat(auth)**: Integrate Google Sign-In and `CredentialManager` for secure user authentication.
- **feat(sync)**: Implement bidirectional Firestore cloud synchronization via `WorkManager`.
- **feat(maps)**: Integrate `GeminiMapsDetector` for location-aware action chips.

### 🛠️ Build

- **build**: Implement full CI/CD pipeline via GitHub Actions for automated build and test verification.

---

## [0.4.0] - Interface Modernization

### 🚀 Features

- **feat(ui)**: Implement `LogHistoryScreen` with advanced hybrid search and filters.
- **feat(ui)**: Implement `ActionableChips` for automated OTP and tracking link detection.
- **feat(ui)**: Implement `SpeedDialSettingsFab` for quick access to backup and sync settings.

### 🎨 UI/UX

- **feat(ui)**: Migrate to Material Design 3 with support for dynamic theming.
- **feat(ui)**: Implement `SplashScreen` for a professional application entry.
- **feat(ui)**: Refactor navigation graph to simplify app flow.

---

## [0.3.0] - Intelligence & Search

### 🚀 Features

- **feat(search)**: Implement `HybridSearchEngine` combining SQLite FTS4 and Vector embeddings using Reciprocal Rank Fusion (RRF).
- **feat(search)**: Implement `VectorEmbeddingHelper` for dense vector generation and cosine similarity.
- **feat(search)**: Implement `DynamicClusterManager` for semantic domain inference (e.g., Finance, Social).
- **feat(storage)**: Implement encrypted database export and CSV utilities.
- **feat(data)**: Implement SQLite Full-Text Search (FTS4) for high-speed keyword indexing.

---

## [0.2.0] - Resilience & Boot

### 🚀 Features

- **feat(storage)**: Implement Direct Boot support for database and preferences (DE storage).
- **feat(boot)**: Implement `BootReceiver` and `UnlockReceiver` for automatic service restoration after reboot.
- **feat(data)**: Implement `DatabaseMigrator` framework for zero-loss schema versioning.

---

## [0.1.0] - Foundation

### 🚀 Features

- **feat(core)**: Implement `AppInitializer` for streamlined startup logic.
- **feat(data)**: Implement base Room database schema for notification logging.
- **feat(service)**: Implement `NotificationListenerService` for real-time system notification capture.
- **feat(ui)**: Implement basic notification list view for captured alerts.

### 🛠️ Build

- **build**: Initialize project structure, Gradle configuration, and base Android manifest.
