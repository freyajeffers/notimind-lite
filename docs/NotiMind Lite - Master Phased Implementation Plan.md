# NotiMind Lite — Master Phased Implementation Plan

## 1. Strategic Roadmap & Architectural Objectives

This master plan tracks the transition from the historical single-tier implementation to the current privacy-focused architecture. Current source already includes Room v19, SQLCipher open-helper wiring for new DE/CE instances, sanitization, Firebase synchronization, and Direct Boot; remaining hardening work is explicitly marked pending and tracked in `todo.md`.

## 2. Phase Dependency Matrix & Sequencing

[Phase 1: Ingestion Sanitization] │ ▼[Phase 2: Cryptographic Hardening] │ ▼[Phase 3: Android OS Boundaries] │ ▼[Phase 4: Secure Data Export] │ ▼[Phase 5: Privacy Observability & Lifecycle]

| Phase | Primary Focus | Dependencies | Target SLA / Latency Budget | Deliverables |

| --- | --- | --- | --- | --- |

| Phase 1 | Ingestion Sanitization & PII Redaction | Existing baseline code | < 5 ms overhead per notification | Regex engine, package filter manager, settings UI for app blacklist |

| Phase 2 | Cryptographic Hardening & Storage Security | Phase 1 | < 15 ms read/write overhead | SQLCipher Room integration, Keystore key manager, encrypted prefs |

| Phase 3 | Android OS Boundaries & Component Hardening | Phase 2 | Zero impact on battery/boot time | Backup rules XML, secured BootReceiver, sanitized intent launcher |

| Phase 4 | Secure Data Export & Sharing Governance | Phases 1, 2, 3 | < 500 ms for 5,000 log export | Export filter dialog, PII scrubber, confirmation modal |

| Phase 5 | Privacy Observability, Lifecycle & Testing | Phases 1–4 | Zero unhandled crashes in chaos tests | R8 log stripper, TTL auto-pruning worker, E2E test suite |

## 3. Phase Summaries & Architectural Boundaries

### Current status

Phase 1 ingestion sanitization is implemented. Phase 2 SQLCipher/Keystore wiring is implemented for new databases, with legacy migration pending. Phase 3 manifest hardening, Phase 4 export governance, and Phase 5 retention/logging hardening are partial and tracked in `todo.md`. Firebase Auth/Firestore sync is implemented and is not an offline-only feature.

### Phase 1: Ingestion Pipeline Sanitization & PII Redaction Engine

Goal: Stop toxic data ingestion at the system boundary. Before any notification touches SQLite or memory caches, it must pass through an extensible sanitization pipeline that redacts OTPs, masking financial values, and drops notifications from blacklisted or sensitive applications.

- Key Components: SanitizationPipeline, PiiRedactionEngine, PackageFilterManager.

- Security Invariant: Raw OTPs, 2FA codes, and blacklisted app payloads must NEVER be written to the Room database.

Phase 2: Cryptographic Hardening & Keystore Storage Security

Goal: Protect data at-rest using SQLCipher open helpers and Keystore-wrapped passphrases for newly created DE/CE databases. Legacy plaintext-install migration, encrypted preferences replacement, and device validation remain pending.

- Key Components: EncryptedDatabaseFactory, KeystoreKeyManager, EncryptedPreferenceManager.

- Security Invariant (target): New database files must use SQLCipher; legacy plaintext files must not be silently accepted after the migration path is implemented.

### Phase 3 status

Current manifest and receivers still require hardening review: `allowBackup` is enabled, `QUERY_ALL_PACKAGES` and `QUICKBOOT_POWERON` remain declared, and device backup/intent behavior has not completed the planned acceptance tests.

- Key Components: Hardened AndroidManifest.xml, data_extraction_rules.xml, SecureBootReceiver, SanitizedIntentLauncher.

- Security Invariant: Zero backup extraction via ADB or cloud; zero execution of unverified broadcast intents; zero raw Intent.parseUri calls without component sandboxing.

Phase 4: Secure Data Export & Sharing Governance

Goal: Provide safe, granular data export mechanics that prevent accidental data over-exposure when sharing logs.

- Key Components: ExportGovernanceManager, ExportFilterDialog, PiiScrubber, EphemeralCacheManager.

- Security Invariant: Unredacted full-database sharing is prohibited without explicit user confirmation, date-range bounds, and optional PII stripping.

Phase 5: Privacy Observability, Lifecycle Management & Verification Gates

Goal: Eliminate passive information leakage, enforce automated data retention policies, and establish regression quality gates.

- Key Components: AutoPruneRetentionWorker, ProGuard/R8 logging rules, SecurityAuditTestSuite (Tiers 1–5).

- Security Invariant: Zero notification content logged to Android Logcat in production; historical logs older than configured TTL are permanently purged.

## 4. Quantitative Performance SLAs & Quality Gates

- Ingestion Latency SLA: Total processing time in NotificationLoggerService.processNotification must remain under 10 ms per event to prevent ANR or system notification drops.

- UI Responsiveness Budget: Database queries and search operations must complete in under 50 ms, maintaining 60 fps (16.6 ms frame budget) in Jetpack Compose LazyColumn rendering.

- Memory Footprint Ceiling: App RSS must not exceed 64 MB under continuous background logging load.

- Test Coverage Gate: Minimum 85% branch coverage on all security, cryptography, and sanitization modules before production release.
