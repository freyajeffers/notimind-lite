# TODO: Unimplemented / Pending Features

This file collects features and implementation tasks mentioned across the repository's Markdown documentation that are NOT fully implemented in the codebase and therefore tracked here. Items are grouped by area and (when helpful) reference the doc where the plan originated.

CONVENTION: When a feature is partially implemented, the remaining work is listed here with the required next steps and acceptance criteria.

---

## Database & Storage (Phase 2)

- Legacy plaintext-to-SQLCipher migration for existing users (critical)
  - Implement and test `DatabaseMigrationOrchestrator` that performs `sqlcipher_export()` or equivalent safe migration.
  - Acceptance: migrate a seeded plaintext DB with N rows and verify exactly N rows exist in encrypted DB; shred plaintext files securely.
  - Current progress: production startup now detects and migrates the active legacy plaintext database only when SQLCipher is enabled; schema-compatible streaming copy verifies inserted rows and exact notification counts before cutover; secure file cleanup, sidecar handling, legacy-file detection, encrypted reopen, ARM64 physical-device validation, and persisted user confirmation before migration are covered.
  - Origin: docs/Phase 2 - Cryptographic Hardening & Keystore Storage Security.md; MASTER_TECHNICAL_SPECIFICATION.md

- Add device/CI acceptance coverage for the full Room migration chain and keep historical schema assets synchronized with `app/schemas`.
  - Current progress: Room schema assets 1..20 are now available under `app/src/main/assets/com.jeffers.notimindlite.data.local.AppDatabase/`; the physical test uses a synthetic v1 fixture and exercises the full migration chain through v20. Physical execution remains the final gate when the device is online.

- Verify hardware-backed Keystore (StrongBox/TEE) coverage on supported devices and fallback tests for devices without StrongBox.
  - Current progress: `SqlCipherKeyManager` and `BackupKeyCodec` now request StrongBox on API 28+ and retry with the platform Keystore/TEE when StrongBox is unavailable. Physical StrongBox and non-StrongBox acceptance tests remain pending.
  - Origin: Phase 2 doc

## Manifest & OS Hardening (Phase 3)

- Add malicious intent-URI tests covering mismatched explicit components and URI grant flags.
  - Current launch parsing uses `URI_ANDROID_APP_SCHEME`, package/component validation, grant-flag stripping, and resolved-package checks.
  - Origin: docs/Phase 3 - Android System Boundaries & Component Hardening.md

- Validate the internal notification-derived app list on supported Android releases and update the Play Store compatibility checklist.
  - `QUERY_ALL_PACKAGES` and `QUICKBOOT_POWERON` were removed; DynamicClusterManager now enriches only packages persisted from received notifications.
  - Origin: docs/Phase 3 - Android System Boundaries & Component Hardening.md

- Finalize `data_extraction_rules.xml` and `fullBackupContent` to ensure DB and prefs excluded from backups and transfers.
  - Origin: Phase 3 doc

## Sanitization & Ingestion (Phase 1)

- Complete performance and ReDoS-hardened regex sets for PiiRedactionEngine and add benchmarks that prove < 3.5ms per notification under burst loads.
  - Origin: docs/Phase 1 - Ingestion Sanitization & PII Redaction Pipeline.md

- PackageFilterManager: add UI settings sync, persistent encrypted preferences, and unit tests for blacklist/whitelist behaviors.
  - Origin: Phase 1 doc

## Export Governance (Phase 4)

- Implement ExportGovernanceManager/ExportFilterDialog and EphemeralExportCache lifecycle (TTL enforcement and automatic pruning).
  - Acceptance: exported files older than TTL are pruned and FileProvider URIs revoked.
  - Origin: docs/Phase 4 - Secure Data Export & Sharing Governance.md

- Implement and test formula-injection sanitization for CSV exports.
  - Origin: Phase 4 doc

## Privacy Observability & Retention (Phase 5)

- Verify release APK bytecode contains no `android.util.Log` callsites after the implemented R8 `assumenosideeffects` rules.
  - Add automated checks in CI to scan APK for log callsites.
  - Origin: docs/Phase 5 - Privacy Observability, Lifecycle & Verification Gates.md

- Validate the existing daily `RetentionCleanupWorker` retention behavior on devices and add release acceptance coverage.
  - Origin: Phase 5 doc

## Backup Notary Server & Notarization

- Remote Notary Server implementation (server-side) and deployment, plus client integration testing with Play Integrity tokens.
  - Acceptance: server verifies Play Integrity token and returns HMAC-SHA256 signature for a submitted file hash.
  - Origin: docs/BACKUP_NOTARY_SERVER.md, docs/Remote Notary Server API Specification

## Cloud Data Deletion Flow (GDPR / Right to Erasure)

- Implement and document a safe, auditable path for cloud data deletion (product review + security rules + repository method change).
  - Origin: API_SPEC.md / docs/policy/room-delete-policy.md

## CI / Tooling

- Fix configuration-cache incompatibilities (e.g., `:app:ensureDebugKeystore` Exec tasks) so Gradle configuration cache can be stored reliably.
  - Origin: Build logs and CI warnings

- Address Dependabot-reported vulnerabilities and upgrade affected dependencies in `gradle/libs.versions.toml` with test runs for regressions.
  - Origin: GitHub Dependabot alerts after recent push

## Tests & QA

- Add migration test coverage for `MIGRATION_18_19` and other critical migrations, and automated E2E device tests for DB migration.

- Add instrumented tests for Direct Boot behavior on a physical device (DE vs CE DB separation).

## Optional / Long-term

- On-device summarization (Daily Digests) using quantized LLMs or TFLite models; productionization requires careful memory and battery testing.
  - Origin: FUTURE_ROADMAP.md

- IntelligentSuggestionEngine feedback loop & telemetry opt-in guardrails (privacy-preserving local learning).

---

To mark an item as implemented, remove it from this file and update the authoritative MD where it originated.
