#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.jeffers.notimindlite}"
SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB+=( -s "$SERIAL" )

"${ADB[@]}" wait-for-device >/dev/null
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell monkey -p "$PACKAGE" 1 >/dev/null
"${ADB[@]}" logcat -d -v threadtime | grep -Ei 'MigrationRunner|SQLCipher|Room' || true
printf '%s\n' 'The current runner is preflight-only; no database copy or cutover is performed.'
