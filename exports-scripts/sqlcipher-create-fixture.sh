#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.jeffers.notimindlite}"
SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB+=( -s "$SERIAL" )

"${ADB[@]}" wait-for-device >/dev/null
"${ADB[@]}" shell run-as "$PACKAGE" sh -c \
  'mkdir -p files/migration-fixtures && : > files/migration-fixtures/notimind_lite_database.plaintext'
printf '%s\n' 'Created an empty app-private plaintext fixture. Populate it only with a test database matching the current Room schema.'
"${ADB[@]}" shell run-as "$PACKAGE" ls -l files/migration-fixtures
