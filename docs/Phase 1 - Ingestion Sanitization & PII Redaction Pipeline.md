# Phase 1 — Ingestion Pipeline Sanitization & PII Redaction Engine

## 1. Scope & Objectives

The objective of Phase 1 is to establish a rigorous, modular pre-ingestion filter between Android's NotificationListenerService hook and the Room database storage layer. This ensures that sensitive personal context—such as multi-factor authentication codes, credit card numbers, financial balances, medication instructions, and notifications from private applications—is either blocked or redacted before any persistence occurs.

## 2. Architectural Components & Responsibilities

| Component | Primary Responsibility | Interaction Boundaries |

| --- | --- | --- |

| PackageFilterManager | Evaluates originating package names against user-configurable Blacklist, Whitelist, and Sensitivity Classification profiles. | Called immediately upon onNotificationPosted() receipt; blocks forbidden apps prior to extraction. |

| PiiRedactionEngine | Executes deterministic, high-performance regex and heuristic pattern matchers across notification title, body, subtext, and bigText fields. | Invoked by SanitizationPipeline to transform raw text into sanitized strings. |

| SanitizationPipeline | Orchestrates the end-to-end sanitization workflow: package filtering, clutter filtering, dynamic debouncing, and text redaction. | Acts as the single entry gate between NotificationLoggerService and NotificationDao. |

| PackageFilterPreferences | Planned persistence for package filtering rules and sensitivity toggles. | Current implementation uses the existing preference layer; encrypted preference migration remains pending. |

## 3. Inter-Module Data Contracts & Entities

// Immutable pre-sanitization ingestion modeldata class RawNotificationData(    val key: String,    val packageName: String,    val rawTitle: String,    val rawContent: String,    val rawSubText: String?,    val rawBigText: String?,    val category: String?,    val channelId: String?,    val postTime: Long)// Sanitized output entity ready for persistencedata class SanitizedNotificationResult(    val shouldPersist: Boolean,    val dropReason: String? = null,    val sanitizedTitle: String,    val sanitizedContent: String,    val sanitizedSubText: String?,    val sanitizedBigText: String?,    val isRedacted: Boolean,    val redactedCategories: List<RedactionCategory>)enum class RedactionCategory {    OTP_2FA,    FINANCIAL_CARD,    FINANCIAL_BALANCE,    SSN_TAX_ID,    HEALTH_MEDICATION}

## 4. Heuristic & Regex Redaction Specifications

The PiiRedactionEngine must enforce deterministic replacement for known sensitive token topologies:

| Target Pattern Type | Pattern Logic / Detection Criteria | Replacement Token |

| --- | --- | --- |

| One-Time Passwords (OTPs) | 4–8 digit standalone numbers following keywords like  code, verification, otp, password, pin, token, secret . | [REDACTED OTP] |

| Payment Card Numbers | 13–19 digit sequences matching Luhn algorithm checks with optional hyphens or spaces. | [REDACTED CARD: ****] |

| US Social Security Numbers | Standard \b\d{3}-\d{2}-\d{4}\b pattern. | [REDACTED SSN] |

| Currency Balances & Debts | Explicit monetary figures exceeding threshold amounts when accompanied by  due, statement, balance, payment . | [AMOUNT REDACTED] |

| Prescription Dosage | Milligram/microgram quantities accompanied by medication keywords (e.g.,  mg, dosage, take, pill ). | [MEDICATION DETAILS REDACTED] |

## 5. Security Invariants & Failure Modes

- Fail-Closed Principle: If an exception occurs within the regex engine or sanitization pipeline during string processing, the notification must be dropped completely rather than written to the database unredacted.

- ReDoS Defense: All regular expressions must be strictly bounded in length and compiled with non-backtracking constructs to prevent Regular Expression Denial of Service (ReDoS) under adversary-crafted notification strings.

- Memory Scrubbing: Temporary string buffers holding raw notification extras must not be retained in memory beyond the immediate scope of the ingestion coroutine.

### Implementation status

`PiiRedactionEngine`, `PackageFilterManager`, and `SanitizationPipeline` are implemented and integrated into notification ingestion. Current matching covers supported OTP, card, phone, email, and currency patterns. SSN, medication-specific masking, encrypted user-configurable package profiles, ReDoS fuzzing, and the stated performance SLA remain pending; see `todo.md`.

## 6. Acceptance Criteria & Verification Tests

- TC-SAN-001 (OTP Redaction): Verify that notifications containing verification codes (e.g., "Your code is 849201") are stored in SQLite with body "Your code is [REDACTED OTP]".

- TC-SAN-002 (Package Exclusion): Verify that apps added to the Blacklist (e.g., banking or healthcare packages) generate zero database records.

- TC-SAN-003 (Performance SLA): 1,000 synthetic notification bursts processed sequentially through the pipeline must average < 3.5 ms per notification on standard ARM64 CPU.
