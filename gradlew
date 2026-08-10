#!/usr/bin/env sh
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
GRADLE_BIN="$SCRIPT_DIR/gradle/gradle-9.3.1/bin/gradle"
exec "$GRADLE_BIN" "$@"
