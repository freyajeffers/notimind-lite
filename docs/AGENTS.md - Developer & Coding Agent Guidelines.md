# AGENTS.md — Developer & Autonomous Coding Agent Guidelines

## 1. Status and Scope

This is a hardening plan, not a statement that every control is already implemented. Current source uses Firebase Auth/Firestore, `INTERNET`, and `ACCESS_NETWORK_STATE`; therefore the older zero-network/offline-only invariant below is historical and does not apply to the current product. The root `AGENTS.md` and current source take precedence.

## 2. Non-Negotiable Security Invariants

Invariant 1: Network access is deliberate and user-scoped

Firebase Auth, Firestore synchronization, and backup notarization are current features. Network calls must remain user-scoped, authenticated, explicit, and free of notification-content logging. Do not add unrelated telemetry or permissions without review.

Invariant 2: Zero Plaintext At-Rest Persistence

All persistent storage must utilize SQLCipher with keys derived from the Android Keystore. Standard plaintext SQLite databases or unencrypted SharedPreferences are forbidden.

Invariant 3: Fail-Closed Sanitization

If a processing error occurs during notification sanitization, the notification must be discarded. Incomplete or unredacted notifications must never reach the database.

Invariant 4: Zero Logcat Payload Residue

Notification titles, bodies, and personal extras must never be passed to Log.d, Log.i, or Log.v in release configurations.

## 3. Concurrency & Database Discipline

- Single-Writer Invariant: Database write transactions must be serialized via a dedicated Room writer dispatcher or coroutine mutex to eliminate database locking contention on SQLCipher pages.

- Non-Blocking Reads: Long-running reads or search queries must run exclusively on Dispatchers.IO, emitting asynchronous Flow states to Jetpack Compose ViewModels.

- Bounded Queue Limits: In-memory notification processing buffers must define strict capacity bounds (max 1,000 items) to apply backpressure during burst notifications.

## 4. Coding & Architecture Standards

| Layer / Subsystem | Allowed Patterns | Forbidden Patterns |

| --- | --- | --- |

| Data Access (DAO) | Parameterized Room queries, indexed query fields, explicit Flow returns. | Raw string concatenation in SQL queries, non-indexed full table scans. |

| Dependency Injection | Constructor injection, manual singleton providers, clear lifecycle scoping. | Heavy reflection-based runtime DI frameworks that inflate startup latency. |

| Intent Launching | Validated PendingIntent dispatch, package-locked explicit intents. | Unchecked Intent.parseUri with implicit component execution. |

| UI Presentation | Stateless Compose composables, unidirectional data flow (UDF), remember caching. | Performing database or disk I/O directly inside composables. |

## 5. Test-Driven Development (TDD) Protocols

Agents must adhere to the Red-Green-Refactor testing protocol:

- Write an explicit boundary test in app/src/test/ defining the expected security or architectural behavior before modifying production code.

- Execute test runner via Gradle; verify failure on the missing feature.

- Implement minimal, targeted code changes to satisfy the test.

- Verify all test suites pass without regression.

- Ensure all tests complete in under 5 seconds under Robolectric.
