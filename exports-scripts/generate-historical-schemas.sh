#!/usr/bin/env bash
set -euo pipefail

# Generate historical Room schema JSON files by checking out the commit
# that last contained a specific AppDatabase version and running assembleDebug.
# Usage: ./generate-historical-schemas.sh 16
# WARNING: this runs a full Gradle build for each version found; time-consuming.

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <version> [version2 ...]"
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PATH="app/src/main/java/com/jeffers/notimindlite/data/local/AppDatabase.kt"
WORKTREE_BASE="$ROOT/.worktrees/schemas"
mkdir -p "$WORKTREE_BASE"

for ver in "$@"; do
  echo "Looking for commit containing version = ${ver}, in $APP_PATH"
  commit=$(git rev-list --all -- "$APP_PATH" | while read cid; do
    if git show ${cid}:$APP_PATH 2>/dev/null | grep -q "version = ${ver},"; then
      echo "$cid"; break
    fi
  done)
  if [ -z "$commit" ]; then
    echo "No commit found setting version=$ver; skipping"
    continue
  fi
  echo "Found commit $commit for version $ver"

  wtdir="$WORKTREE_BASE/${ver}_${commit:0:8}"
  if [ -d "$wtdir" ]; then
    echo "Worktree already exists: $wtdir"
  else
    git worktree add -f "$wtdir" "$commit"
  fi

  pushd "$wtdir" >/dev/null
  echo "Building app in worktree $wtdir (this will take a while)"
  ./gradlew :app:assembleDebug --no-daemon --console=plain

  schema_dir="app/schemas/com.jeffers.notimindlite.data.local.AppDatabase"
  if [ -d "$schema_dir" ]; then
    mkdir -p "$ROOT/app/schemas/com.jeffers.notimindlite.data.local.AppDatabase"
    cp "$schema_dir/${ver}.json" "$ROOT/app/schemas/com.jeffers.notimindlite.data.local.AppDatabase/" || {
      echo "Schema ${ver}.json not found in worktree; build may not have emitted it"
    }
  else
    echo "Schema dir $schema_dir not present in worktree"
  fi
  popd >/dev/null

  # keep the worktree for inspection but you can remove it with git worktree remove
  echo "Done processing version $ver"
done

echo "All requested versions processed."
