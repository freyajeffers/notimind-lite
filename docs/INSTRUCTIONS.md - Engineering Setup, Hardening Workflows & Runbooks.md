# INSTRUCTIONS.md — Engineering Setup, Workflows & Runbooks

## 1. Workstation Prerequisites & Toolchain

- **JDK**: OpenJDK 17.
- **Android SDK**: `compileSdk = 37`, `minSdk = 33`, `targetSdk = 36`.
- **Gradle/Kotlin**: Use the checked-in Gradle wrapper and version catalog; do not copy the older SDK/version values from this document.

- Host OS: Linux (Arch, Ubuntu 22.04 LTS), macOS, or Windows (WSL2).

## 2. Dependency Synchronization Guide

Update app/build.gradle.kts to include the hardened security toolchain:

dependencies { // SQLCipher for Android (Room SQLite encryption) implementation("net.zetetic:android-database-sqlcipher:4.5.4") implementation("androidx.sqlite:sqlite-ktx:2.4.0") // AndroidX Security Crypto (EncryptedSharedPreferences & Keystore MasterKeys) implementation("androidx.security:security-crypto:1.1.0-alpha06") // AndroidX WorkManager (Automated retention & TTL pruning) implementation("androidx.work:work-runtime-ktx:2.9.0") // Existing Room & Compose dependencies retained implementation(libs.androidx.room.runtime) implementation(libs.androidx.room.ktx) ksp(libs.androidx.room.compiler)}

## 3. Step-by-Step Implementation Runbook

### Phase 1: Ingestion Sanitization Integration

- Create com.jeffers.notimindlite.sanitization package.

- Implement PiiRedactionEngine.kt with compiled regex patterns for OTP, Luhn card checks, and currency values.

- Implement PackageFilterManager.kt integrating blacklisted package filtering.

- Hook SanitizationPipeline into NotificationLoggerService.processNotification prior to dao.insertNotification().

### Phase 2: SQLCipher & Keystore Storage

Implemented for newly opened DE/CE databases through `EncryptedDatabaseFactory` and `SqlCipherKeyManager`. Pending work is legacy plaintext database migration, device/StrongBox validation, and production migration testing.

### Phase 3: Manifest & OS Boundary Locking

- Update AndroidManifest.xml: set android:allowBackup="true" with dataExtractionRules and fullBackupContent excluding databases.

- Remove QUERY_ALL_PACKAGES; add targeted <queries> tags.

- Harden BootReceiver.kt: remove QUICKBOOT_POWERON; add UID sanity checks.

- Update NotificationLauncher.kt to sanitize parsed intents.

### Phase 4: Export Governance UI & FileProvider

- Create ExportFilterDialog.kt in Compose UI allowing date range and package selection.

- Update DatabaseExporter.kt to accept filter criteria and execute scoped queries.

- Enforce 15-minute TTL auto-pruning on cacheDir/exports/.

### Phase 5: Observability & Verification

- Update app/proguard-rules.pro with -assumenosideeffects rules stripping Logcat calls.

- Implement AutoPruneRetentionWorker.kt and register periodic daily WorkManager request in NotiMindApp.onCreate().

- Execute full Gradle test verification suite.

## 4. Security Verification Commands

# 1. Run full unit and boundary test suite./gradlew test# 2. Verify ADB backup exclusion (must produce empty/near-empty archive without DB)adb backup -f test_backup.ab -noapk com.jeffers.notimindlitetar -tf test_backup.ab 2>/dev/null || echo "Backup successfully restricted"# 3. Verify SQLCipher at-rest encryption via ADB shelladb shell "su -c 'sqlite3 /data/data/com.jeffers.notimindlite/databases/notimind_lite_database \".tables\"'"# Expected output: "Error: file is not a database"# 4. Verify Logcat hygiene in release APKadb logcat -cadb shell am start -n com.jeffers.notimindlite/.ui.MainActivityadb logcat -d | grep -i "NotificationLoggerSrv"# Expected output: 0 matching log entries
