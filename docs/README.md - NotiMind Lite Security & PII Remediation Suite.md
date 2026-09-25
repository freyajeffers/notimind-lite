NotiMind Lite — Security Hardening & Phased Remediation Suite

A comprehensive, privacy-first technical documentation suite detailing the architectural vulnerabilities, PII ingestion risks, STRIDE threat mitigations, and 5-phase engineering implementation roadmap for the NotiMind Lite Android notification logger.

## 1. Project Vision & Security Architecture

NotiMind Lite is an Android notification management and archiving application engineered for privacy-conscious users. It uses local-first notification capture and search, plus optional authenticated cloud synchronization and backup notarization. This documentation suite records implemented controls and remaining hardening work.

## 2. Complete Documentation Suite Index

| Document Name | Scope & Description | Key Topics |

| --- | --- | --- |

| 01. Project Overview | System mission, STRIDE threat matrix, PII ingestion audit findings, and target architecture. | STRIDE analysis, PII taxonomy, system topology diagram. |

| 02. Master Phased Implementation Plan | Master roadmap bridging current state with hardened target state across 5 phases. | Phase dependency matrix, inter-module contracts, performance SLAs. |

| 03. Phase 1 Implementation Plan | Ingestion Pipeline Sanitization & PII Redaction Engine. | Package blocklist, regex OTP masking, Luhn algorithm card check. |

| 04. Phase 2 Implementation Plan | Cryptographic Hardening & Keystore Storage Security. | SQLCipher Room integration, Keystore key wrapping, offline DB migration. |

| 05. Phase 3 Implementation Plan | Android OS System Boundaries & Component Hardening. | Backup rules XML, BootReceiver anti-spoofing, sanitized intent launcher. |

| 06. Phase 4 Implementation Plan | Secure Data Export & Sharing Governance. | Granular export filtering, pre-export PII scrubber, confirmation modal. |

| 07. Phase 5 Implementation Plan | Privacy Observability, Lifecycle Management & Verification Gates. | R8 logcat stripping, daily TTL auto-pruning worker, 5-tier test suite. |

| 08. AGENTS.md | Developer and autonomous coding agent guidelines and operational invariants. | Non-negotiable constraints, TDD protocol, latency budgets. |

| 09. INSTRUCTIONS.md | Engineering setup, step-by-step implementation runbooks, and audit commands. | JDK/SDK prerequisites, build.gradle dependencies, ADB verification runbooks. |

| 10. README.md | Central repository landing page and security documentation navigation index. | Quickstart guide, document links, architecture summary. |

## 3. Key Security Highlights

- **Networked features are explicit**: Firebase Auth/Firestore synchronization and backup notarization use declared network access; notification content is not used for unrelated telemetry.
- **At-rest encryption**: SQLCipher-backed Room open helpers are wired for new DE/CE database instances, with Keystore-wrapped per-database passphrases. Legacy plaintext database migration remains pending.
- **Pre-Ingestion PII Redaction**: Supported credentials, verification codes, phone numbers, emails, currency values, and card numbers are sanitized before persistence; broader category coverage remains pending.

- **Backup extraction controls**: `data_extraction_rules.xml` and `backup_rules.xml` are present and exclude protected database/preferences domains; `allowBackup` remains enabled and must be validated on supported Android versions.

## 4. Quickstart Verification

# Clone repository and verify tests./gradlew testDebugUnitTest# Compile hardened release APK with ProGuard R8 stripping./gradlew assembleRelease
