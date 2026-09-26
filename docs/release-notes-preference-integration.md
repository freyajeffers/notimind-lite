# Release Notes Draft: Full User Preference Integration

## What’s new

- Configure notification capture scope, package filters, importance, attachments, ongoing notifications, and foreground-only capture.
- Choose FTS4 keyword search, semantic vector search, semantic ranking, or a combination of search features.
- Set notification retention, export encryption, and cloud-sync scheduling constraints.
- Manage privacy controls for PII redaction, title anonymization, and auto-delete on read.
- Configure security controls for encrypted backups, passphrase protection, and database auto-lock.
- Tune telemetry, low-memory behavior, database limits, and vector cache size from Advanced & Developer settings.

## Safety defaults

Debug builds keep capture enabled, retain notifications indefinitely, and disable destructive privacy controls so development and verification data is not lost. Release builds expose the configured retention and privacy controls.

## Verification checklist

- [ ] Settings labels and summaries load from localized resources.
- [ ] Preference changes persist after leaving and reopening Settings.
- [ ] Capture, search, sync, privacy, security, and storage consumers observe updated values.
- [ ] Debug safeguards prevent destructive preference changes.
- [ ] Release builds allow supported retention and privacy changes.
- [ ] Android build, unit tests, and lint pass.
