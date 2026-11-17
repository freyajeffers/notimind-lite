# Phase 5 — Privacy Observability, Lifecycle Management & Verification Gates

## 1. Scope & Objectives

Phase 5 establishes operational longevity and regression immunity. It addresses information leakage through system telemetry (Logcat), enforces automated data retention lifecycles via background workers, and deploys a 5-tier testing and verification framework to ensure security guarantees persist across future releases.

## 2. Current implementation status

Current source includes SQLCipher and AndroidX Security R8 keep rules, release log stripping, encrypted backup utilities, a daily retention worker, and tiered JVM/device test infrastructure. The complete five-tier adversarial gate and release APK bytecode verification remain pending; see `todo.md`.

## 3. Architectural Components & Responsibilities

| Component | Primary Responsibility | Design & Hardening Details |

| SafeLogger Wrapper | Encapsulates all logging calls; completely disables debug and verbose logging in release builds. | Prevents accidental leakage of notification extras or titles to Android Logcat. |

| ProGuard / R8 Rules | Strip android.util.Log call-sites during release APK compilation and preserve security-provider reflection targets. | Implemented in `app/proguard-rules.pro`; release APK bytecode verification remains a gate. |

| AutoPruneRetentionWorker | AndroidX WorkManager periodic task running daily to prune dismissed notifications exceeding the retention TTL. | Runs in background under battery-friendly constraints (device idle, charging). |

| SecurityQualityGateSuite | Automated test pipeline executing unit, boundary, pairwise, and adversarial penetration tests prior to release. | Blocks deployment if security invariants or performance SLAs are violated. |

## 3. ProGuard / R8 Log Stripping Rules

In app/proguard-rules.pro, add explicit bytecode stripping rules for logging methods:

# Strip all debug, verbose, and informational logging calls in release builds-assumenosideeffects class android.util.Log { public static boolean isLoggable(java.lang.String, int); public static int v(...); public static int d(...); public static int i(...);}# Preserve cryptographic security classes and SQLCipher native bindings-keep class net.zetetic.database.sqlcipher.** { \*; }-dontwarn net.zetetic.database.sqlcipher.**-keep class androidx.security.crypto.\*_ { _; }

## 4. Automated Retention Lifecycle & WorkManager Policy

To prevent indefinite data accumulation and unbounded storage growth:

The existing `RetentionCleanupWorker` is scheduled daily by `AppInitializer`, reads the configured retention policy, and deletes eligible dismissed records. Export cache cleanup uses a 15-minute default TTL under `cacheDir/exports/`.

## 5. 5-Tier Verification & Testing Architecture

| Test Tier | Scope & Methodology | Target Coverage & Exit Criteria |

| --- | --- | --- |

| Tier 1: Unit Tests | PiiRedactionEngine regex correctness, Luhn algorithm verification, CSV sanitization escaping. | 100% pass rate; > 90% branch coverage on sanitizers. |

| Tier 2: Boundary Tests | SQLCipher encryption verification, Keystore rotation under key invalidation, ADB backup exclusion. | Zero plaintext bytes found on raw database disk reads. |

| Tier 3: Pairwise Integration | Service ingestion to encrypted Room write; UI settings toggle to filter rule synchronization. | StateFlow updates UI within 16 ms of database commit. |

| Tier 4: Chaos & Burst Testing | 500-notification burst over 5 seconds; rapid service reconnects; system reboot simulation. | Zero database deadlocks; peak memory < 64 MB; 0 dropped active notifications. |

| Tier 5: Adversarial Penetration | Spoofed broadcast injection, ReDoS regex fuzzing, intent redirection exploits, formula injection. | Zero unhandled exceptions; 100% exploit payload neutralization. |
