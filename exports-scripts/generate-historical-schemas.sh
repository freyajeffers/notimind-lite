#!/usr/bin/env bash
set -euo pipefail

# Generate historical Room schema JSON files by checking out the commit that
# contains a requested AppDatabase version and running only the KSP task needed
# to emit that version's schema. Historical commits predate the current Gradle
# catalog and Kotlin compiler DSL, so build-only compatibility files are copied
# into the temporary worktree; production source remains historical.
# Usage: ./generate-historical-schemas.sh 8 9 10

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <version> [version2 ...]" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PATH="app/src/main/java/com/jeffers/notimindlite/data/local/AppDatabase.kt"
WORKTREE_BASE="$ROOT/.worktrees/schemas"
SCHEMA_PACKAGE="com.jeffers.notimindlite.data.local.AppDatabase"
mkdir -p "$WORKTREE_BASE"

prepare_build_inputs() {
  local worktree="$1"
  mkdir -p "$worktree/gradle"
  cp "$ROOT/gradle/libs.versions.toml" "$worktree/gradle/libs.versions.toml"
  cp "$ROOT/gradle.properties" "$worktree/gradle.properties"
  cp "$ROOT/debug.keystore" "$worktree/debug.keystore"
  printf '\nandroid.newDsl=false\nandroid.builtInKotlin=false\n' >> "$worktree/gradle.properties"

  # Older snapshots contain JSON-escaped Kotlin source (for example, `\\"`
  # inside Room @Query annotations). Normalize that extraction artifact only in
  # the temporary worktree so KSP can parse the historical sources.
  python3 - "$worktree/app/src/main" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
for path in root.rglob("*.kt"):
    text = path.read_text()
    normalized = text.replace('\\"', '"')
    if normalized != text:
        path.write_text(normalized)
PY

  # Historical databases often disabled Room export. Enable it only in the
  # temporary source so KSP emits the requested JSON without changing history.
  python3 - "$worktree/app/src/main/java/com/jeffers/notimindlite/data/local/AppDatabase.kt" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
text = text.replace("exportSchema = false", "exportSchema = true", 1)
path.write_text(text)
PY

  # Kotlin 2.4 reports the historical kotlinOptions DSL as a hard error.
  # Replace only that build-script block; this does not alter production source.
  python3 - "$worktree/app/build.gradle.kts" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
start = text.find("  kotlinOptions {")
if start < 0:
    raise SystemExit(0)
end = text.find("\n  }", start)
if end < 0:
    raise SystemExit("cannot locate historical kotlinOptions block")
end += len("\n  }")
replacement = '''  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
  }'''
path.write_text(text[:start] + replacement + text[end:])
PY
}

for ver in "$@"; do
  echo "Looking for commit containing version = ${ver}, in $APP_PATH"
  commit=$(git rev-list --all -- "$APP_PATH" | while read -r cid; do
    if git show "$cid:$APP_PATH" 2>/dev/null | grep -q "version = ${ver},"; then
      echo "$cid"
      break
    fi
  done)
  if [ -z "$commit" ]; then
    echo "No commit found setting version=$ver; inspect migration history manually" >&2
    continue
  fi

  worktree="$WORKTREE_BASE/${ver}_${commit:0:8}"
  if [ ! -d "$worktree" ]; then
    git worktree add -f "$worktree" "$commit"
  fi
  prepare_build_inputs "$worktree"

  # Room only exports when the historical build supplies a schema location.
  python3 - "$worktree/app/build.gradle.kts" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
if "room.schemaLocation" not in text:
    text += '\n\nksp {\n  arg("room.schemaLocation", "$projectDir/schemas")\n}\n'
path.write_text(text)
PY

  echo "Generating schema ${ver} in $worktree"
  "$ROOT/gradlew" --project-dir "$worktree" \
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 \
    :app:kspDebugKotlin --no-daemon --max-workers=2 --console=plain

  schema="$worktree/app/schemas/$SCHEMA_PACKAGE/${ver}.json"
  if [ ! -f "$schema" ]; then
    echo "Schema was not emitted: $schema" >&2
    exit 1
  fi
  mkdir -p "$ROOT/app/schemas/$SCHEMA_PACKAGE"
  cp "$schema" "$ROOT/app/schemas/$SCHEMA_PACKAGE/"
  echo "Copied $schema"
done

echo "All requested versions processed."
