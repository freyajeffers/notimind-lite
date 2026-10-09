#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.jeffers.notimindlite.validation}"
TEST_PACKAGE="${TEST_PACKAGE:-${PACKAGE}.test}"
SERIAL="${ANDROID_SERIAL:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
TEST_APK="${TEST_APK:-$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB+=( -s "$SERIAL" )

[[ -f "$APK" ]] || { printf 'Missing app APK: %s\n' "$APK" >&2; exit 1; }
[[ -f "$TEST_APK" ]] || { printf 'Missing test APK: %s\n' "$TEST_APK" >&2; exit 1; }

mapfile -t DEVICES < <("${ADB[@]}" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
[[ "${#DEVICES[@]}" -eq 1 || -n "$SERIAL" ]] || {
  printf 'Require exactly one online device or set ANDROID_SERIAL; found %s.\n' "${#DEVICES[@]}" >&2
  exit 2
}

"${ADB[@]}" wait-for-device >/dev/null
"${ADB[@]}" install -r "$APK" >/dev/null
"${ADB[@]}" install -r "$TEST_APK" >/dev/null
[[ -n "$("${ADB[@]}" shell pm path "$PACKAGE")" ]] || { printf 'App package not installed.\n' >&2; exit 1; }
[[ -n "$("${ADB[@]}" shell pm path "$TEST_PACKAGE")" ]] || { printf 'Test package not installed.\n' >&2; exit 1; }

printf '%s\n' 'Validation uses isolated device_migration_* database files and does not replace the app database.'
"${ADB[@]}" shell run-as "$PACKAGE" sh -c 'ls -l databases 2>/dev/null || true'
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am instrument -w -r \
  -e class com.jeffers.notimindlite.migration.EncryptedMigrationDeviceTest \
  "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
"${ADB[@]}" logcat -d -v threadtime | grep -Ei 'EncryptedMigrationDeviceTest|SQLCipher|AndroidRuntime|FATAL EXCEPTION' || true
