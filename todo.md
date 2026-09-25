# TODO: Unimplemented / Pending Features

This file collects features and implementation tasks mentioned across the repository's Markdown documentation that are NOT fully implemented in the codebase and therefore tracked here. Items are grouped by area and (when helpful) reference the doc where the plan originated.

CONVENTION: When a feature is partially implemented, the remaining work is listed here with the required next steps and acceptance criteria.

---

## Database & Storage (Phase 2)

- Legacy plaintext-to-SQLCipher migration for existing users (critical)
  - Implement and test `DatabaseMigrationOrchestrator` that performs `sqlcipher_export()` or equivalent safe migration.
  - Acceptance: migrate a seeded plaintext DB with N rows and verify exactly N rows exist in encrypted DB; shred plaintext files securely.
  - Origin: docs/Phase 2 - Cryptographic Hardening & Keystore Storage Security.md; MASTER_TECHNICAL_SPECIFICATION.md

- Add formal migration tests using Room's MigrationTestHelper for each historical version (1..18) or create a synthetic schema generator to emit valid `<version>.json` files.
  - Origin: docs/room-schema-backfill.md

- Verify hardware-backed Keystore (StrongBox/TEE) coverage on supported devices and fallback tests for devices without StrongBox.
  - Origin: Phase 2 doc

## Manifest & OS Hardening (Phase 3)

- Decide and implement action for `QUERY_ALL_PACKAGES` vs `<queries>` scoping: remove or scope, update Play Store compatibility checklist.
  - Origin: docs/Phase 3 - Android System Boundaries & Component Hardening.md

- Remove `QUICKBOOT_POWERON` or add sender UID validation and add tests simulating rogue broadcasts.
  - Acceptance: sending spoofed QUICKBOOT_POWERON causes no state changes.
  - Origin: Phase 3 doc

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

- ProGuard/R8 rules to strip debug/verbose logging and verify no notification content remains in release logs.
  - Add automated checks in CI to scan APK for log callsites.
  - Origin: docs/Phase 5 - Privacy Observability, Lifecycle & Verification Gates.md

- Implement AutoPruneRetentionWorker periodic WorkManager job and validate retention TTL behavior on devices.
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
