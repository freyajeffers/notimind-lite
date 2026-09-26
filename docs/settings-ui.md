# Settings UI and User Preferences

The Settings screen exposes the runtime preferences backed by `PreferencesRepository`. Changes are applied while the app is running and are consumed by capture, search, sync, privacy, and storage components.

## Preference catalog

| Section | Controls | Runtime effect |
| --- | --- | --- |
| Capture | Capture notifications, foreground-only capture, attachments, ongoing notifications, package allowlist/blocklist, minimum importance, actions-only capture | Filters which incoming notifications are persisted. |
| Data & Search | Cloud sync, vector search, FTS4, semantic ranking, retention days, encrypted exports | Selects search engines, controls retention cleanup, and protects exported backups. |
| Privacy & Redaction | Auto-delete on read, anonymize titles, redact PII | Reduces sensitive data retained or displayed locally. |
| Security & Encryption | Encrypted backups, required passphrase, database auto-lock | Adds protection around exported data and database access. |
| Cloud Sync | Sync interval, Wi-Fi-only, charging-only, last-sync status, sync now | Controls background sync constraints and manual synchronization. |
| Advanced & Developer | Telemetry, low-memory mode, maximum database size, vector cache size | Tunes diagnostics, memory use, and storage/cache limits. |

All user-facing labels and summaries live in `app/src/main/res/values/strings.xml`. Keep new preference copy there rather than hard-coding text in composables.

## Build-aware behavior

Debug builds preserve captured data for testing. Destructive controls such as auto-delete on read and title anonymization are disabled and forced to safe values; capture remains enabled and retention is indefinite. Release builds expose the corresponding controls and apply the configured values.

## Verification checklist

- [ ] Open **Settings** and confirm each section renders with a title and summary.
- [ ] Toggle capture, sync, FTS4, vector search, semantic ranking, export encryption, and privacy controls; leave and reopen Settings to confirm persistence.
- [ ] Enter retention values at the documented 1–3650 day range and confirm invalid input does not replace the current value.
- [ ] Confirm sync constraints are reflected in the background work request.
- [ ] Confirm debug builds show disabled destructive controls and the explanatory debug note.
- [ ] Confirm release builds allow retention, export encryption, capture, auto-delete, and anonymization changes.
- [ ] Run the Android unit/test and lint checks before release.

## Release notes draft

NotiMind Lite now provides a complete preference experience: users can control capture scope, search engines, retention, sync constraints, export encryption, privacy redaction, and database security from Settings. Debug builds use data-preserving safeguards, while release builds honor the selected retention and privacy policies.
