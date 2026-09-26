#!/usr/bin/env bash
# ADB helper: read or write a preference key in notimind_lite_prefs
# Usage: adb-toggle-prefs.sh get <key>
#        adb-toggle-prefs.sh set <key> <value>
# Requires adb to be in PATH and a device/emulator connected.
PKG=com.jeffers.notimindlite
PREF=notimind_lite_prefs
CMD="$1"
KEY="$2"
VAL="$3"
if [[ "$CMD" == "get" ]]; then
  adb shell "run-as $PKG cat /data/data/$PKG/shared_prefs/$PREF.xml" | sed -n "s/.*name=\"$KEY\" value=\"\(.*\)\"\/>/\1/p"
  exit 0
fi
if [[ "$CMD" == "set" ]]; then
  # Using settings put is simpler for demo, but only supports basic types; fallback to xml edit is risky.
  adb shell "run-as $PKG /bin/sh -c 'printf \"<?xml version=\'1.0\' encoding=\'utf-8\'?>\n<map>\n  <string name=\"$KEY\">$VAL</string>\n</map>\n\' > /data/data/$PKG/shared_prefs/$PREF.xml'"
  adb shell "run-as $PKG chmod 660 /data/data/$PKG/shared_prefs/$PREF.xml"
  echo "Wrote $KEY=$VAL (may require app restart)"
  exit 0
fi
cat <<EOF
Usage: $0 get <key>
       $0 set <key> <value>
EOF
