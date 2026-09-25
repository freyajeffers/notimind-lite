#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.jeffers.notimindlite}"
SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB+=( -s "$SERIAL" )

"${ADB[@]}" wait-for-device
"${ADB[@]}" shell pm path "$PACKAGE"
"${ADB[@]}" shell dumpsys package "$PACKAGE" | grep -E "versionName|versionCode" || true

# Capture device and app state before a migration test.
"${ADB[@]}" shell getprop ro.build.version.sdk
"${ADB[@]}" shell getprop ro.hardware.keystore || true
"${ADB[@]}" shell df -h /data
"${ADB[@]}" logcat -c

# Run the app, then inspect migration-related logs. The prototype is feature-gated
# and does not migrate data until a production trigger is wired.
"${ADB[@]}" shell monkey -p "$PACKAGE" 1 >/dev/null
"${ADB[@]}" logcat -d -v threadtime | grep -Ei 'MigrationRunner|SQLCipher|Room|NotiMind' || true

# Direct Boot smoke sequence; device policy may require an interactive unlock.
"${ADB[@]}" reboot
printf '%s\n' 'Device reboot requested. Verify DE behavior before unlock, then unlock and rerun log capture.'
