# NotiMind Lite — Security Architecture & Comprehensive PII Audit

## 1. Executive Summary & System Mission

NotiMind Lite is an Android notification management and local archiving application engineered for privacy-conscious users who require offline-first notification aggregation without cloud telemetry, analytics SDKs, or external network dependencies. The application hooks into Android's NotificationListenerService API, capturing active notifications, archiving historical dismissals, and providing a modern Jetpack Compose interface for status monitoring, log exploration, search, and local data export.

A comprehensive security and PII audit was conducted across the source code tree (com.jeffers.notimindlite) and authentic device runtime export snapshots. While the application successfully omits android.permission.INTERNET—thereby preventing remote data exfiltration—the audit revealed critical vulnerabilities at the local device, OS boundary, and ingestion layers. Without corrective hardening, high-sensitivity personal context (financial debts, therapy reminders, medical prescriptions, personal messages, and session URLs) remains vulnerable to local extraction, unsanitized export leakage, broadcast spoofing, and unencrypted at-rest compromise.

## 2. Comprehensive Threat Model (STRIDE Framework)

| Threat Category | Target Component | Vulnerability / Exploit Vector | Risk Rating | Planned Mitigation |

| --- | --- | --- | --- | --- |

| Spoofing | BootReceiver | Unprotected custom broadcast action QUICKBOOT_POWERON permits third-party apps to forge boot events and trigger rogue notification restoration. | MEDIUM | Enforce strict UID verification (Binder.getCallingUid() == Process.SYSTEM_UID) or require signature-level custom permission. |

| Tampering | AppDatabase (SQLite) | Unencrypted Room database file allows direct byte tampering on rooted devices or physical access, altering audit records. | HIGH | Migrate Room database to SQLCipher with 256-bit AES-GCM encryption rooted in Android Keystore. |

| Repudiation | DatabaseExporter | Cleartext log history can be exported and modified without cryptographic signing or tamper-evident integrity hashes. | LOW | Introduce HMAC-SHA256 integrity metadata headers for exported archives. |

| Information Disclosure | Ingestion Pipeline & Storage | Indiscriminate logging of OTPs, 2FA tokens, banking transactions, medical alerts, and full PII into plaintext database tables. | CRITICAL | Implement automated regex/heuristic redaction engine, sensitive app blacklist, and category-level opt-in controls. |

| Information Disclosure | AndroidManifest.xml | android:allowBackup="true" allows unencrypted extraction of notification database via adb backup without root permissions. | HIGH | Set allowBackup="false" or configure restrictive data_extraction_rules.xml excluding the database domain. |

| Information Disclosure | Logcat Telemetry | Debug logs emit raw notification titles and body strings directly into Android Logcat buffer. | LOW | Strip Log.d / Log.v calls in release builds via ProGuard/R8 rules and structured logger wrapper. |

| Denial of Service | NotificationLoggerService | High-velocity notification floods can exhaust SQLite write queues or trigger database lock contention. | MEDIUM | Enforce SQLite WAL mode with 5000ms busy timeout, dynamic 30s debouncing, and bounded batch write transactions. |

| Elevation of Privilege | NotificationLauncher | Unsafe Intent.parseUri() fallback execution allows Intent Redirection vulnerabilities if stored URIs are compromised. | MEDIUM | Sanitize parsed intents, restrict to URI_ANDROID_APP_SCHEME, validate component packages, and drop URI permission grant flags. |

## 3. Detailed PII Taxonomy & Sensitive Data Ingestion Profile

Analysis of actual production logs confirms that NotificationLoggerService captures unstructured text containing multiple protected sensitive data categories. The table below outlines the specific data categories discovered in the runtime export:

| Sensitivity Domain | Captured Attributes & Pattern Types | Originating Package Examples | Regulatory & Security Impact |

| --- | --- | --- | --- |

| Financial Records | Card payment due dates, minimum balances due, loan statement amounts, installment advances. | com.google.android.apps.messaging, com.moneylion, com.google.android.apps.tasks | PCI-DSS / GLBA concern; enables financial profiling, spear-phishing, or social engineering attacks. |

| Health & Medical Data | Prescription medication reminders, therapy/counseling appointment follow-ups, specialist clinic referrals. | com.sec.android.app.shealth, com.samsung.android.app.reminder, com.google.android.apps.tasks | HIPAA / Sensitive Personal Data violation; reveals physical and mental health treatment status. |

| Communications & Contacts | Personal SMS messages from family, incoming call phone numbers, private recruiter messages. | com.google.android.apps.messaging, com.linkedin.android, com.att.mobilesecurity | Breaches conversational confidentiality and exposes direct communications graph. |

| Identity & Accounts | Personal Proton email addresses, job application candidate IDs, corporate connection requests. | ch.protonmail.android, com.joinhandshake.student | Direct PII leakage; enables targeted credential stuffing and unauthorized identity correlation. |

| Session & Token URLs | Deep links containing internal email IDs, web session identifiers, and meeting sync tokens. | com.google.android.apps.tasks | Session hijacking and unauthorized token replay risks if export files are shared. |

## 4. End-to-End System Topology & Hardened Architecture

+-----------------------------------------------------------------------------------------+|                                 Android Operating System                                ||  [StatusBarNotification]  --->  [NotificationListenerService] (Ingestion Boundary)      |+-----------------------------------------------------------------------------------------+                                             |                                             v+-----------------------------------------------------------------------------------------+|                           Layer 1: Sanitization & Redaction                             ||  - Package Blocklist & Inclusion Filter (Exclude Banking, Health, Auth, Messaging)      ||  - Heuristic & Regex Masking Engine (OTPs, Account Numbers, SSNs, Balances)             ||  - Clutter & 30s Dynamic Debounce Filter                                                |+-----------------------------------------------------------------------------------------+                                             |  (Sanitized Notification Entity)                                             v+-----------------------------------------------------------------------------------------+|                        Layer 2: Storage Security & Cryptography                         ||  - SQLCipher for Android (256-bit AES-GCM / PBKDF2 Key Derivation)                      ||  - Android Keystore System (Hardware-backed Master Key)                                ||  - SQLite WAL Mode + 5000ms Busy Timeout + Encrypted SharedPreferences                   |+-----------------------------------------------------------------------------------------+                                             |                                             v+-----------------------------------------------------------------------------------------+|                         Layer 3: OS Boundary & Manifest Controls                        ||  - allowBackup="false" & data_extraction_rules.xml (Zero ADB/Cloud Extraction)          ||  - Protected BootReceiver (UID Enforcement, QUICKBOOT Removal)                          ||  - Scoped IntentLauncher (Component Validation, Flag Sanitization)                      ||  - Strict Package Visibility (<queries> over QUERY_ALL_PACKAGES)                        |+-----------------------------------------------------------------------------------------+                                             |                                             v+-----------------------------------------------------------------------------------------+|                     Layer 4: Secure Data Export & Sharing Governance                    ||  - Pre-Export Filtering (Date Range, Category, App Package Whitelist)                   ||  - Export PII Scrubbing Pass (Irreversible Pseudonymization)                            ||  - Explicit Sensitivity Warning & Confirmation Dialog                                   ||  - Scoped Ephemeral FileProvider Sharing with Immediate Cleanup                         |+-----------------------------------------------------------------------------------------+

## 5. Phased Remediation Roadmap Overview

The remediation strategy is divided into five sequential, dependency-ordered engineering phases:

- Phase 1: Ingestion Pipeline Sanitization & PII Redaction Engine — Intercepts and sanitizes notifications before database persistence, redacting OTPs, financial balances, and sensitive app payloads.

- Phase 2: Cryptographic Hardening & Keystore-Backed Storage Security — Eliminates plaintext at-rest storage by migrating Room to SQLCipher with hardware-backed Android Keystore key management.

- Phase 3: Android OS System Boundaries & Component Hardening — Shuts down external attack surfaces by disabling insecure backups, locking down BootReceiver, and securing intent execution.

- Phase 4: Secure Data Export & Sharing Governance — Enhances export safety with user-controlled date/package filtering, export PII stripping, and warning confirmations.

- Phase 5: Privacy Observability, Lifecycle Management & Verification Gates — Strips debug Logcat output in production builds, enforces automated data retention/TTL auto-pruning, and integrates comprehensive security regression tests.
