# Changelog

All notable changes to NotiMind Lite are documented here. This project adheres to [Conventional Commits](https://www.conventionalcommits.org/).

---

## [Unreleased] - Preference Runtime, UX Polish & Reliability
- **docs(ux)**: Add an adversarial UX review of the v21 onboarding and navigation flow, with a filtered finding table and eight drafted tickets.

- **fix(ux)**: collapse the ~20 Settings sections into six labelled categories with saved expansion state, keeping the notification-access card pinned above them.
- **fix(ux)**: move the encryption, passphrase and auto-lock controls out of "Appearance & behaviour" and into "Privacy & security", which is what the category header promises, and compose each category explicitly so the grouping cannot drift.
- **fix(a11y)**: make each settings category header a single button carrying a show/hide action label so screen readers announce what the header does, and stop announcing the decorative chevron twice.
- **fix(ux)**: replace notification-gate jargon with plain language and a next-step, distinguish the three history empty states so filters are never blamed when none are set, echo the search term on a miss, and explain the export app filter when no apps exist yet.
- **feat(selectors)**: Choose apps by recorded notifications instead of installation state, so uninstalled apps stay filterable while apps with no records are hidden.
- **storage(migration)**: Bump the database to v21 and rebuild `notifications` during the 20→21 upgrade so the declared foreign keys, column defaults, indexes and FTS triggers match the exported schema; orphaned notifications keep their history through synthesised parent rows rather than being deleted.
- **fix(ui)**: Resolve configuration-aware resource lookup lint errors in restore toast/snackbar messages.
- **security(deps)**: Raise the forced Android Guava resolution to 33.7.2-android, the first patched version for CVE-2026-102554.
- **security(deps)**: Align Gradle resolution rules with patched releases for Guava, Bouncy Castle, Logback, jose4j, JDOM, Commons Lang, and Apache HttpClient so transitive advisory paths cannot select vulnerable versions.
- **security(mobsf)**: Route production logging through a debug-only AppLogger, enable screenshot/tapjacking protections, and document intentional Room raw-SQL and non-applicable control findings in the MobSF policy.
- **security(ci)**: Add a MobSFScan/Semgrep job that builds and scans the debug APK, uploads JSON reports, excludes test sources from production scanning, and explicitly disables backups in the debug manifest overlay.
- **security(ci)**: Pin MobSFScan/Semgrep, fail on unapproved MobSF findings, verify the patched Guava resolution, and upload the dependency-resolution evidence with the scan reports.
- **ci(quality)**: Wrap the five production log statements that exceeded Detekt's 120-character limit so the Android quality job can complete without suppressions.
- **ci(codeql)**: Disable Gradle build and configuration caches during CodeQL's Android build so the extractor observes Kotlin and Java compilation instead of reusing cached outputs.
- **build(config)**: Separate Firebase and Supabase configuration directories and organize backend source, initialization, telemetry, audit, sync, and tests under matching subdirectories; local database migrations now live under `data/local/migration`, and Gradle reads Supabase client settings from `config/supabase/supabase.properties`.
- **security(crypto)**: Complete Phase 2 storage hardening with AndroidX `EncryptedSharedPreferences`, versioned AndroidKeyStore `MasterKey` rotation for Supabase PKCE/session secrets, legacy-session migration, device rotation coverage, and portable-backup key-management guidance.
- **security(network)**: Pin the dedicated Backup Notary host to the official Let's Encrypt ISRG Root X1 SPKI and reject any non-HTTPS or unexpected-host endpoint before connecting.
- **security(android-boundaries)**: Remove the broad `QUERY_ALL_PACKAGES` permission and spoofable `QUICKBOOT_POWERON` receiver action; retain launcher-scoped package visibility and add an unauthorized-broadcast regression test.
- **security(intent)**: Harden persistent notification launch intents with Android-app URI parsing, component/package validation, URI-grant stripping, and resolved-activity ownership checks.
- **privacy(package-visibility)**: Build dynamic app enrichment from the persisted notification-derived app list instead of enumerating all installed applications.
- **security(lifecycle)**: Add defense-in-depth R8 log stripping and security-provider keep rules, document the existing daily retention worker, and enforce a 15-minute default export-cache TTL.
- **privacy(selectors)**: Hide uninstalled observed apps from active package selectors while retaining their notification history and local observed-app records.
- **privacy(icons)**: Store notification app-icon snapshots under durable app files instead of cache storage, with lazy migration for existing cached paths.
- **security(export)**: Sanitize package names as well as notification fields during CSV export to close spreadsheet formula-injection coverage gaps.
- **security(export)**: Add reusable inclusive-start/exclusive-end date and package filters to the export boundary, rejecting invalid ranges and blank package filters.
- **ci(security)**: Enforce five independently reported adversarial test tiers in GitHub Actions: feature, boundary, pairwise, real-world, and security-regression coverage.
- **a11y**: Group headers now announce app, notification count, latest title and expansion state, so cards from the same app no longer share an identical spoken label.
- **a11y**: Fix Accessibility Scanner findings: pin/open controls now expose unique, context-bearing descriptions (`Pin <title> from <app>`), the 24dp pin button is a 48dp touch target, group pin/expand actions name their group, and checkbox/switch controls carry their row label instead of reading "not checked"/"Off".
- **feat(export)**: Add a notification export surface in Settings with time-window presets, per-app filtering, a live selection count, and an explicit confirmation dialog that states format, scope, and whether the file will be encrypted.
- **feat(export)**: Deliver exports through either the system share sheet or a user-chosen document location. The confirmation dialog now offers Share and Save to device, both operating on the same generated file, and the transient cache copy is removed once a save succeeds.
- **security(export)**: Gate exports behind device-credential confirmation when the biometric-export preference is enabled, and surface export success or failure instead of failing silently.
- **storage(migrations)**: Make the historical migration path create missing backup metadata tables safely and replace the incomplete Room identity-hash test with a synthetic version-1 migration fixture.
- **test(migrations)**: Add historical Room schema assets for versions 1–16 and a full v1-to-v20 seeded migration-preservation test.
- **test(migration)**: Validate every exported Room schema JSON against the migration that produces it, using `MigrationTestHelper` over instrumentation assets rather than hand-written DDL, with nine documented historical drift pairs baselined.
- **a11y(actions)**: Grow card pin, open, group pin and group expand buttons to the 48dp touch-target minimum while keeping the icons visually unchanged.
- **a11y(labels)**: Give pin, open, restore and details actions unique spoken labels that include the notification title and app, and label settings switches and list checkboxes, so screen readers no longer announce identical text for different items.
- **a11y(icons)**: Mark notification app icons as decorative so talkback no longer repeats "App icon" on every card.
- **fix(a11y)**: Show "Unknown dismissal reason" instead of leaking the raw `Reason #%1$d` format string into spoken text.
- **test(migrations)**: Align Android Room migration-test serialization dependencies to 1.8.1 and force the matching runtime on test configurations.
- **security(codeql)**: Add a scheduled and manually dispatchable CodeQL workflow for Android Java/Kotlin, using manual build tracing against the debug variant and least-privilege code-scanning permissions.

- **build(fdroid)**: Use monotonic date-based debug version codes (`YYYYMMDDNN`) for F-Droid archive builds; counters are never reset.

- **docs**: Align project phase-ordering and verification guidance across agent instructions.

- **style(ktlint)**: Fix all 14 Kotlin style findings and add the KtLint Gradle check to the Android static-analysis workflow.
- **style(lint)**: Convert SharedPreferences, Uri, and bitmap creation call sites to AndroidX KTX APIs; rename all 106 flagged composables to PascalCase; reintegrate the 169 unused strings, colors, and styles into real UI/theme call sites. Detekt, KtLint, and Android Lint now report zero findings.
- **style(ui)**: Use AndroidX `String.toUri()` for external actionable-chip links while retaining `Uri.encode()` for map query construction.
- **fix(perf)**: Replace the embedding-executor non-null assertion with synchronized local reuse/recreation; test reuse and shutdown behavior.
- **fix(notification)**: Reject null sanitized titles explicitly instead of asserting non-null in notification capture.
- **fix(notification)**: Rebuild boot-restored notifications closer to their source using persisted icons, text/styles, timestamps and categories; open the source app on tap, reapply enabled PII redaction, and keep title grouping app-scoped.
- **feat(notification)**: Capture bounded MessagingStyle messages/senders, expanded labels, color/visibility/count/progress/chronometer state, and resolvable large-icon resource references for restoration. Metadata is redacted at capture when enabled, redacted again before posting, and kept local-only.
- **feat(notification)**: Add swipe-to-dismiss on notification cards, per-notification restore controls directly on cards, group-level restore, and restore-all actions on the Recently Dismissed section header.
- **feat(notification)**: Add a Settings action to restore recent dismissals, per-notification restore/dismiss controls in expanded cards, and a persistent FIFO queue capped at 50 active restored notifications; overflow remains queued and is promoted when an in-app dismissal frees a slot.
- **fix(notification)**: Reconcile Room state with notification-listener snapshots on reconnect and screen resume; show restored cards in Active, move dismissed cards to Dismissed/Lost, and skip restore candidates that the listener confirms are still active.
- **fix(notification)**: Keep restored alerts ongoing and non-clearable by a shade swipe, but explicitly cancel their status-bar entry when dismissed in NotiMind; source-app reposts do not dismiss restored alerts.
- **feat(data)**: Add Room migration 19→20 for restore metadata, register the full migration chain for both CE and DE databases, and preserve local-only metadata when sync imports replace notification rows.
- **cleanup(notification)**: Remove unreachable pre-API-33 app-label lookup; `minSdk` is 33, so package queries use the API 33+ flags overload consistently.
- **fix(sync)**: Bound Supabase PKCE, refresh, notification-sync, and settings-sync requests; close connections on every exit and handle empty/malformed refresh error bodies safely. Backup notarization now serializes JSON safely and always disconnects after its bounded request.
- **security(auth)**: Migrate legacy plaintext Supabase tokens to Android Keystore-backed encrypted preferences; encrypted values take precedence, and plaintext fallback is removed after a successful atomic migration.
- **build**: Replaced deprecated Groovy space-assignment syntax in Android Gradle properties; `--warning-mode all` now reports only Android Gradle Plugin and Detekt plugin deprecations.
- **deps**: Update Firebase BoM to 35.1.0 and Fragment KTX to 1.9.1, resolving the current Gradle lint dependency-update findings.
- **test(coverage)**: Add backup-notary tests for JSON payloads, POST headers/timeouts, trimmed signatures, non-200 rejection, response-read failure cleanup, online gating, nonce/hash/token handoff, Play Integrity nonce/token extraction, and mapped security failures using injected network and integrity stages.
- **test(coverage)**: Add retention-worker tests for finite cleanup policy, pinned/active/recent row preservation, retain-all behavior, retry on the database lock gate, and invalid stored zero-day settings; use both JVM and on-device worker builders.
- **test(coverage)**: Point JaCoCo at the current AGP JVM execution-data output, use current compiled-class directories, and ingest only valid connected Android `.ec` files.
- **test(coverage)**: Add `BackupFileFormat` cases for valid keystore-only headers and upper-bound rejection of PBKDF2 iterations and wrapped-DEK length.
- **test(coverage)**: Add Supabase sync utility tests for payload round-trips/defaults, local-only restore-metadata exclusion, record identities, nullable JSON parsing, URL encoding, and HTTP success/error/disconnect behavior including 199/299 status boundaries through an injected connection factory.
- **test(coverage)**: Add `DatabaseExporter` tests for JSON file defaults, missing/malformed restore inputs, and rejected export settings; use argument validation so the existing error handler catches disabled-format/authentication failures instead of leaking `IllegalStateException`.
- **test(coverage)**: Add value-contract unit tests for session, resource, app, notification-group, Room relation, FTS, and suggestion-search models, covering defaults, copy behavior, payloads, and equality.
- **test(coverage)**: Exercise default DAO read-state dispatch for empty batches, read/delete modes, and mark-all behavior.
- **test(coverage)**: Verify persisted migration consent fails closed on unknown stored values and rejects attempts to persist UNKNOWN.
- **test(coverage)**: Cover the database auto-lock preference gate for enabled and disabled security combinations.
- **test(coverage)**: Exercise feature-gate preference snapshots, network capability/exception paths, and app-label success/failure paths with non-vacuous assertions.
- **fix(clustering)**: Match domain markers against normalized tokens, allowing longer marker prefixes, so incidental substrings such as `cat` inside `application` do not assign the pets domain while `bank` still matches `banking`.
- **fix(clustering)**: Ignore blank app labels during keyword enrichment instead of inserting whitespace-only search terms.
- **test(coverage)**: Add device-level cluster-manager coverage for installed-app initialization/idempotence, PackageManager SecurityException/NameNotFound recovery, initialized hierarchy, flattened subcluster lookup, case-insensitive/domain/subcategory matching, unknown-token filtering, application-label normalization, domain inference/fallback, null and short labels, and keyword-enrichment match/no-match paths.
- **test(coverage)**: Exercise restored-notification posting, batch deduplication, boot-group restore and overflow, queued-item promotion/cancellation, grouped dismissal cleanup, and the empty boot-group path in focused Robolectric tests.
- **test(receiver)**: Await BootReceiver background restoration jobs before closing in-memory Room fixtures, preventing late database queries from leaking into following tests.
- **test(coverage)**: Enable debug Android instrumentation coverage so device execution can be measured alongside JVM tests.
- **test(ui)**: Wait for the splash handoff before asserting main-navigation tabs in Compose instrumentation tests; add pure tests for history search suggestions and notification grouping/sorting contracts.
- **test(notification)**: Add focused parser tests for app-label success/security fallback, inbox-line cast fallback, icon availability, and launch-intent success and failure paths.
- **ci**: Run Android CI on `remediation/**` pushes; gate release APK jobs behind the explicit `RELEASE_SIGNING_ENABLED` repository variable so unsigned builds are not attempted by default.
- **fix(backup)**: Clear a copied backup key when the dialog is dismissed or confirmed, as well as after its 30-second expiry, without clearing clipboard content replaced by the user.
- **fix(backup)**: Restrict legacy raw-IV restore fallback to sufficiently sized files that do not carry the versioned `NMB1` magic, preventing malformed versioned backups from falling through to legacy decryption.
- **build(version)**: Derive `versionName` from the nearest Git release tag and 12-character commit hash; mark untagged checkouts and dirty working trees in the version string. Keep `versionCode` sourced from `version.properties`.
- **fix(notification)**: Recheck listener access and catch Room SQL exceptions in asynchronous callbacks; suppress only known Android USB-debugging/charging status clutter, and correct lifecycle fixtures for unlocked boot and cleared notification snapshots.

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

- **fix(navigation)**: Honor the system rotation preference and save/restore tab back stacks so each screen's scroll position survives bottom-navigation switches.
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
