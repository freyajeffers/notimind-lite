# Changelog

All notable changes to NotiMind Lite are documented here. This project adheres to [Conventional Commits](https://www.conventionalcommits.org/).

---

## [Unreleased] - Preference Runtime, UX Polish & Reliability

### 🚀 Features & Runtime Integration

- **feat(prefs)**: Apply profile defaults, capture filters, performance tuning, maintenance scheduling, and runtime preference accessors across capture, sync, search, retention, and UI modules.
- **feat(retention)**: Add preference-driven cleanup scheduling and maintenance workers.
- **feat(backup)**: Enforce export-format and anonymization preferences at the backup boundary.
- **feat(settings)**: Add performance tuning, maintenance, export-format, anonymization, and preference-backup controls with user-facing descriptions and localized labels.
- **feat(search)**: Integrate preference-controlled FTS/vector search behavior, shared recent searches, package/date filters, reset controls, query tokenization, and match highlighting.
- **refactor(search)**: Consolidate duplicate intelligence engines while preserving hybrid search and suggestion behavior.

### 🗃️ Data Migration & Storage Safety

- **feat(migration)**: Add guarded on-device plaintext dry-run diagnostics, streaming copy orchestration, atomic database cutover, rollback handling, and a migration command receiver.
- **fix(migration)**: Harden SQLCipher cutover table filtering and startup migration exception handling.
- **fix(storage)**: Load SQLCipher and scroll-related settings reliably from the preferences repository.
- **fix(boot)**: Move `PreferencesRepository` to Device Protected storage to prevent the boot-time crash before credential-encrypted storage is available.

### 🎨 UI, Accessibility & Localization

- **feat(ui)**: Add log sorting options, shared filters/recent queries, search reset behavior, notification metadata highlighting, and a unified settings layout.
- **polish(ui)**: Improve notification card icon prefetching, active-section filtering, action placement, tooltip positioning, touch targets, and debug-disabled settings scrolling.
- **feat(i18n,a11y)**: Complete localization of displayed notification content, sections, metadata, controls, filters, quick actions, splash content, and accessibility descriptions.
- **a11y(ui)**: Add semantics and labels for search fields, page controls, actionable-chip icons, and previously unlabeled controls.
- **fix(theme)**: Share preference state between navigation and Settings and make AMOLED surfaces/system bars pure black.
- **fix(ui)**: Replace deprecated tooltip/icon APIs and restore stable navigation icons after accessibility polish.

### 🛡️ Reliability & Compatibility

- **fix(service)**: Tolerate OEM notification extras including bitmap icons and string arrays.
- **fix(preferences)**: Correct the embedding API and add performance accessors.
- **build**: Update Robolectric to 4.17, remove avoidable lint/configuration warnings, and make debug-keystore generation configuration-cache compatible.
- **test**: Stabilize `NotificationLoggerServiceTest` by polling for insertion instead of relying on fixed sleeps.
- **chore(detekt)**: Refresh the static-analysis baseline and resolve remaining findings.

### 🚀 Existing Unreleased Features

- **feat(prefs)**: Complete runtime preference integration for capture, cloud sync, FTS4 and semantic search, retention, export encryption, privacy/redaction, security, and advanced tuning controls.
- **feat(settings)**: Expose preference state and actions in the Settings UI, with build-aware safeguards that keep destructive options disabled in debug builds.

### 📝 Release Notes Draft

- Give users direct control over what NotiMind captures, how long notifications are retained, and which search indexes are enabled.
- Add privacy and security controls for PII redaction, title anonymization, encrypted exports, passphrase protection, and database auto-lock.
- Add cloud-sync scheduling constraints, cache and database sizing, and clear debug-build behavior for data-preserving defaults.
- See [`docs/settings-ui.md`](docs/settings-ui.md) for the preference catalog and verification checklist.

### 🚀 Existing Unreleased Features

- **feat(sanitization)**: Implement `PiiRedactionEngine` for deterministic pre-ingestion redaction of OTPs, Luhn-verified cards, phone numbers, emails, and currency values.
- **feat(sanitization)**: Implement `PackageFilterManager` and fail-closed `SanitizationPipeline` for package filtering and ingestion protection.
- **feat(prefs)**: Add PII Redaction toggle in `PreferenceManager` and `SettingsScreen`.
- **feat(crypto)**: Implement `CryptoUtils` with RFC 5869 HKDF-SHA256 key derivation and AES-256-GCM authenticated encryption.
- **feat(crypto)**: Implement `KeyManager` wrapper for AndroidX Security MasterKeys with JVM test fallback.
- **feat(crypto)**: Wire SQLCipher-backed Room open helpers for both Direct Boot databases, with per-database Keystore-wrapped passphrases.
- **feat(data)**: Advance the Room baseline to schema v19 with notification-group persistence and explicit `MIGRATION_18_19`.

### ✅ QA & Build Verification

- **build**: `assembleDebug` passes with the debug APK generated successfully.
- **test**: `testDebugUnitTest` passes with 204 tests executed, 204 passed, 0 failed (1 intentionally skipped historical migration scaffold).
- **fix**: Corrected JVM-safe feature-flag parsing, notification grouping by persisted group key, and Robolectric coverage for preference/action tests.

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
