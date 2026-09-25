# Phase 4 — Secure Data Export & Sharing Governance

## 1. Scope & Objectives

Phase 4 re-architects the data export workflow. In the baseline system, users can inadvertently dump their entire notification history into cleartext files shared via an open Android share sheet. Phase 4 introduces granular export filtering (time bounds, app selections), pre-export PII scrubbing, explicit sensitivity warnings, and ephemeral cache lifecycle management.

## 2. Architectural Components & Responsibilities

| Component | Primary Responsibility | Design & Security Protocols |

| --- | --- | --- |

| ExportGovernanceManager | Coordinates query execution against Room, applies user filter parameters, and invokes serialization workers. | Queries only the requested slices of history; never pulls unconstrained full-database tables into memory. |

| ExportFilterDialog | Compose UI dialog offering date ranges (Last 24h, 7d, 30d, All), package-level inclusion checkboxes, and format options. | Enforces explicit user choices before file creation is permitted. |

| ExportPiiScrubber | Performs secondary irreversible pseudonymization/masking on exported strings if user enables "Anonymized Export" mode. | Replaces contact names, email handles, and phone numbers with cryptographic hashes or generic placeholders. |

| EphemeralExportCache | Manages files inside cacheDir/exports/; generates scoped content URIs via FileProvider and enforces strict auto-deletion. | Deletes export files older than 15 minutes and immediately flushes previous exports prior to generating new ones. |

## 3. User Consent & Sensitivity Warning Modal

Mandatory Confirmation Gate: Before invoking Intent.ACTION_SEND, the application displays an audit confirmation summary card displaying:

- Selected date range and total record count.

- Number of distinct applications included.

- Presence of sensitive categories (financial, medical, or messaging apps detected in the batch).

- Explicit warning: "Exported files contain unencrypted notification text. Ensure you trust the recipient application."

## 4. Ephemeral FileProvider Lifecycle Protocol

To prevent abandoned export files from lingering in device storage:

// Ephemeral cache manager with aggressive TTL cleanupclass EphemeralExportCache(private val context: Context) {    private val exportsDir = File(context.cacheDir, "exports")        fun createEphemeralExportFile(extension: String): File {        pruneStaleExports(maxAgeMs = 15 * 60 * 1000L) // 15-minute TTL        if (!exportsDir.exists()) exportsDir.mkdirs()        return File(exportsDir, "notimind_export_${System.currentTimeMillis()}.$extension")    }        fun pruneStaleExports(maxAgeMs: Long) {        val now = System.currentTimeMillis()        exportsDir.listFiles()?.forEach { file ->            if (now - file.lastModified() > maxAgeMs) {                file.delete()            }        }    }}

## 5. Security Invariants & Sanitization Controls

- Full-Database Export Ban: Default export scope must be bounded to the last 7 days unless the user explicitly checks "Export Full History".

- Formula Injection Immunity: All exported fields in CSV format must undergo sanitizeCsvField() validation to neutralize spreadsheet command injection.

- Scoped URI Grant: Intent.FLAG_GRANT_READ_URI_PERMISSION is granted strictly to the target chosen activity; write permissions are strictly omitted.

## 6. Current implementation status

`DatabaseExporter` implements CSV formula-injection escaping and scoped content URI sharing. The governance dialog, date/package filtering, secondary export PII scrubber, and 15-minute TTL cache manager described below remain pending; see `todo.md`.

## 7. Acceptance Criteria & Verification Tests

- TC-EXP-001 (Date Boundary Filter): Request export for "Last 24 Hours"; verify that no notification older than 24 hours appears in the resulting JSON/CSV.

- TC-EXP-002 (Package Filtering): Uncheck messaging packages in the filter dialog; verify that zero SMS or messaging records are serialized into the export payload.

- TC-EXP-003 (Cache Expiration): Generate export file; advance device clock by 20 minutes; verify file is purged from cacheDir/exports/ upon next app interaction.
