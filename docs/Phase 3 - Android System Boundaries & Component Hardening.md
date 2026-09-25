# Phase 3 — Android System Boundaries & Component Hardening

## 1. Scope & Objectives

Phase 3 hardens the Android operating system perimeter around NotiMind Lite. It addresses attack vectors originating from inter-process communication (IPC), broadcast spoofing, over-privileged manifest declarations, unprotected backup channels, and unsafe intent deserialization.

## 2. Current status

Backup/data-extraction rules and the FileProvider are present. `QUICKBOOT_POWERON` remains declared, `QUERY_ALL_PACKAGES` remains intentionally declared under the root project policy, and the planned full broadcast/intent/backup acceptance tests remain pending.

## 3. Architectural Components & Responsibilities

| Component | Vulnerability Addressed | Hardened Architecture |

| AndroidManifest.xml | Uncontrolled data extraction via ADB/cloud backup; over-broad package inventory permissions. | Declare allowBackup="false" or configure strict extraction rules; remove QUERY_ALL_PACKAGES in favor of scoped <queries>. |

| BootReceiver | Spoofed broadcast injection via unprotected QUICKBOOT_POWERON action. | Drop QUICKBOOT_POWERON action or verify calling UID; enforce sender validation for system broadcasts. |

| NotificationLauncher | Intent Redirection vulnerabilities and unsafe URI deserialization. | Sanitize parsed intents: enforce URI_ANDROID_APP_SCHEME, validate component package ownership, strip URI grant flags. |

| FileProvider Configuration | Accidental file traversal or over-scoped storage access via content URIs. | Restrict provider paths exclusively to a private subfolder in cacheDir/exports/; disable external storage access. |

## 3. Manifest Hardening & Backup Extraction Rules

The application manifest must be updated to explicitly lock down backup mechanisms:

<!-- AndroidManifest.xml Hardening --><application    android:name=".NotiMindApp"    android:allowBackup="true"    android:dataExtractionRules="@xml/data_extraction_rules"    android:fullBackupContent="@xml/backup_rules"    ... ><!-- res/xml/data_extraction_rules.xml (Android 12+) --><data-extraction-rules>    <cloud-backup>        <exclude domain="database" path="." />        <exclude domain="sharedpref" path="." />    </cloud-backup>    <device-transfer>        <exclude domain="database" path="." />        <exclude domain="sharedpref" path="." />    </device-transfer></data-extraction-rules>

## 4. BootReceiver Protection & Anti-Spoofing Protocol

To prevent rogue applications from triggering notification storms or resource exhaustion:

- Remove android.intent.action.QUICKBOOT_POWERON from the manifest <intent-filter>.

- Inside BootReceiver.onReceive, verify that the intent action equals Intent.ACTION_BOOT_COMPLETED or Intent.ACTION_MY_PACKAGE_REPLACED.

- Assert that isInitialStickyBroadcast() is handled safely and prevent redundant execution loops.

## 5. Secure Intent Launching & Redirection Mitigation

In NotificationLauncher.kt, replace raw Intent.parseUri calls with strict validation:

// Hardened intent parsing and validation protocolfun safeLaunchNotification(context: Context, packageName: String, intentUri: String?) {    if (intentUri.isNullOrBlank()) {        fallbackToPackageLaunch(context, packageName)        return    }    try {        // Enforce URI_ANDROID_APP_SCHEME and strip dangerous flags        val parsedIntent = Intent.parseUri(intentUri, Intent.URI_ANDROID_APP_SCHEME)                // Ensure parsed intent targets the expected package        parsedIntent.`package` = packageName        parsedIntent.component?.let { comp ->            if (comp.packageName != packageName) {                throw SecurityException("Mismatched component package in parsed intent")            }        }                // Strip URI permission grant flags to prevent privilege escalation        parsedIntent.flags = parsedIntent.flags and (            Intent.FLAG_GRANT_READ_URI_PERMISSION.inv() and            Intent.FLAG_GRANT_WRITE_URI_PERMISSION.inv() and            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION.inv()        )        parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)                context.startActivity(parsedIntent)    } catch (e: Exception) {        fallbackToPackageLaunch(context, packageName)    }}

## 6. Acceptance Criteria & Verification Tests

- TC-BOUND-001 (ADB Backup Rejection): Execute adb backup -f test.ab -noapk com.jeffers.notimindlite; verify that extracted archive contains 0 bytes of database or preference files.

- TC-BOUND-002 (Rogue Broadcast Rejection): Send an unprivileged broadcast for QUICKBOOT_POWERON using adb shell am broadcast -a android.intent.action.QUICKBOOT_POWERON; verify receiver ignores the intent and posts zero notifications.

- TC-BOUND-003 (Intent Redirection Defenses): Craft a malicious intentUri pointing to an unexported internal activity; verify NotificationLauncher rejects the redirection.
