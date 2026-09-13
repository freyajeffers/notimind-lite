# Phase 5 — Privacy Observability, Lifecycle Management & Verification Gates

## 1. Scope & Objectives

Phase 5 establishes operational longevity and regression immunity. It addresses information leakage through system telemetry (Logcat), enforces automated data retention lifecycles via background workers, and deploys a 5-tier testing and verification framework to ensure security guarantees persist across future releases.

## 2. Architectural Components & Responsibilities

| Component | Primary Responsibility | Design & Hardening Details |

| --- | --- | --- |

| SafeLogger Wrapper | Encapsulates all logging calls; completely disables debug and verbose logging in release builds. | Prevents accidental leakage of notification extras or titles to Android Logcat. |

| ProGuard / R8 Rules | Configures bytecode optimizer to strip android.util.Log.d and Log.v call-sites during APK compilation. | Guarantees zero log overhead and zero data residue in production binaries. |

| AutoPruneRetentionWorker | AndroidX WorkManager periodic task running daily to prune dismissed notifications exceeding the retention TTL. | Runs in background under battery-friendly constraints (device idle, charging). |

| SecurityQualityGateSuite | Automated test pipeline executing unit, boundary, pairwise, and adversarial penetration tests prior to release. | Blocks deployment if security invariants or performance SLAs are violated. |

## 3. ProGuard / R8 Log Stripping Rules

In app/proguard-rules.pro, add explicit bytecode stripping rules for logging methods:

# Strip all debug, verbose, and informational logging calls in release builds-assumenosideeffects class android.util.Log {    public static boolean isLoggable(java.lang.String, int);    public static int v(...);    public static int d(...);    public static int i(...);}# Preserve cryptographic security classes and SQLCipher native bindings-keep class net.zetetic.database.sqlcipher.** { *; }-dontwarn net.zetetic.database.sqlcipher.**-keep class androidx.security.crypto.** { *; }

## 4. Automated Retention Lifecycle & WorkManager Policy

To prevent indefinite data accumulation and unbounded storage growth:

// Daily WorkManager task for automatic TTL pruningclass AutoPruneRetentionWorker(    context: Context,    workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {    override suspend fun doWork(): Result {        val prefManager = EncryptedPreferenceManager(applicationContext)        val retentionDays = prefManager.getRetentionDays() // e.g. 30 days        if (retentionDays <= 0) return Result.success() // 0 = indefinite                val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 86_400_000L)        val db = EncryptedDatabaseFactory.getDatabase(applicationContext)                // Execute atomic deletion of non-pinned dismissed records older than cutoff        db.notificationDao().deleteDismissedOlderThan(cutoffTimestamp)        return Result.success()    }}

## 5. 5-Tier Verification & Testing Architecture

| Test Tier | Scope & Methodology | Target Coverage & Exit Criteria |

| --- | --- | --- |

| Tier 1: Unit Tests | PiiRedactionEngine regex correctness, Luhn algorithm verification, CSV sanitization escaping. | 100% pass rate; > 90% branch coverage on sanitizers. |

| Tier 2: Boundary Tests | SQLCipher encryption verification, Keystore rotation under key invalidation, ADB backup exclusion. | Zero plaintext bytes found on raw database disk reads. |

| Tier 3: Pairwise Integration | Service ingestion to encrypted Room write; UI settings toggle to filter rule synchronization. | StateFlow updates UI within 16 ms of database commit. |

| Tier 4: Chaos & Burst Testing | 500-notification burst over 5 seconds; rapid service reconnects; system reboot simulation. | Zero database deadlocks; peak memory < 64 MB; 0 dropped active notifications. |

| Tier 5: Adversarial Penetration | Spoofed broadcast injection, ReDoS regex fuzzing, intent redirection exploits, formula injection. | Zero unhandled exceptions; 100% exploit payload neutralization. |
