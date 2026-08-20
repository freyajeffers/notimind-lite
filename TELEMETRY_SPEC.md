# Telemetry & Observability Dashboard Specification

This document specifies the telemetry and observability framework for NotiMind Lite, focusing on app stability and feature adoption while adhering to a "Privacy-First" architecture.

## 1. Core Principles
- **Anonymity by Default**: No User IDs, Device IDs, or PII (emails, phone numbers, notification content) are collected.
- **Opt-Out/Strict Mode**: All telemetry is disabled when 'Strict Privacy' mode is enabled.
- **Aggregated Insights**: Data is viewed as aggregates to prevent individual user fingerprinting.

## 2. Crash Reporting (Firebase Crashlytics)
**Goal**: Monitor app stability and resolve regressions.

### Metrics to Track:
- **Crash-Free Users (%)**: Primary stability KPI.
- **Top Crashes by Impact**: Sorted by number of affected users.
- **Device/OS Distribution**: Identify OS-specific bugs (e.g., Android 15 issues).

### Privacy Guardrails:
- **PII Scrubbing**: All custom keys passed to Crashlytics must be scrubbed via `TelemetryManager.scrubPII()`.
- **No Content Logging**: Notification titles, content, and subtext are strictly forbidden from crash reports.
- **Contextual Metadata**: Use generic keys like `screen_name` or `action_type` instead of specific data values.

## 3. Feature Usage (Firebase Analytics)
**Goal**: Understand which tools are valuable to users to guide development.

### Event Specifications:
| Event Name | Parameters | Description |
| :--- | :--- | :--- |
| `filter_used` | `type` (app\|reason), `value` (reasonCode) | Tracks which search filters are most active. |
| `chip_clicked` | `entity_type` (OTP\|URL\|LOCATION) | Tracks adoption of actionable extraction chips. |
| `sync_triggered` | `method` (manual\|auto) | Monitors cloud backup usage. |
| `privacy_mode_toggled` | `enabled` (true\|false) | Tracks adoption of Strict Privacy mode. |

## 4. Performance Monitoring
**Goal**: Ensure "Zero-Jank" UI experience.

### Key Performance Indicators (KPIs):
- **Search Latency**: Time from query input to result emission (Target: < 100ms).
- **Embedding Latency**: Time to compute vectors for a search query.
- **Memory Pressure Events**: Frequency of `TRIM_MEMORY` callbacks.

## 5. Strict Privacy Mode Kill-Switch
- **Logic**: When `PreferenceManager.isStrictPrivacyEnabled() == true`:
    - `FirebaseAnalytics.setAnalyticsCollectionEnabled(false)`
    - `FirebaseCrashlytics.setCrashlyticsCollectionEnabled(false)`
    - `TelemetryManager.logFeatureUsage()` returns immediately.
    - `TelemetryManager.reportNonFatal()` returns immediately.
