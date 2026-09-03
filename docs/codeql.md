# CodeQL Configuration

This document explains the CodeQL configuration in
[`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml) and the
rationale behind each non-default choice.

## Why these settings

### `languages: java-kotlin`

NotiMind Lite is a Kotlin-first Android app with no Java sources. The
`java-kotlin` extractor covers both — it runs the Java extractor and the
Kotlin extractor against the same build output. No matrix split is needed
because there are no Java-only modules to analyze separately.

### `build-mode: manual` + `./gradlew assembleDebug`

CodeQL offers two build modes:

- **`none`**: no build is run. The extractor analyzes whatever classes
  happen to be on the classpath (usually empty for an Android project).
  Fast, but catches nothing.
- **`manual`**: the workflow runs an explicit build step. The extractor
  observes the build's classpath and analyzes everything that gets compiled.

For an Android project where Kotlin files reference Android framework
classes (`android.content.Context`, `android.app.Notification`, etc.),
the `none` mode produces zero useful findings. `manual` is required.

The build step (`./gradlew assembleDebug`) uses:

- `--no-daemon`: daemon-forbidden (some CI runners fail with `OutOfMemoryError`
  when a daemon is reused across jobs).
- `--no-build-cache`: the build cache could mask freshly-introduced
  bugs by serving stale class files. Disabling it guarantees CodeQL sees
  the actual current sources.
- `-Dkotlin.compiler.execution.strategy="in-process"`: bypasses Gradle's
  daemon-based Kotlin compiler pool. Same reason.
- `-Dkotlin.incremental=false`: forces full recompilation. Same reason.

These flags are borrowed from the official CodeQL Android example and are
not negotiable without breaking analysis.

### `queries: security-extended`

CodeQL ships with two built-in query packs:

- **`security-and-quality`**: ~150 queries covering security issues
  AND lint-grade quality issues. High recall, more noise.
- **`security-extended`**: ~200 queries covering security issues
  AND additional medium-severity security patterns. Low noise,
  high signal.

We chose `security-extended` because the CI's job is to catch security
findings — quality issues are caught by Detekt + Android Lint (see
`ci.yml`'s `static-analysis` job). Bumping to `security-and-quality`
would create duplicate reporting for things Detekt already flags.

If you want stricter coverage during security reviews, override the
`queries:` input with `security-and-quality` on a per-run basis.

### `fetch-depth: 0`

The previous value was `2` (just enough for the current commit and its
parent). CodeQL uses git history to compute PR diff context and to
determine which lines were added in this PR (for alert suppression on
existing findings). Full history (`0`) is the safer default.

Cost: a slightly larger checkout. On this repo that's negligible (~100
commits, a few MB).

### `persist-credentials: false`

By default, `actions/checkout` writes the workflow's `GITHUB_TOKEN` to
the local git config so subsequent steps can push back. For a CodeQL
job there is no "push back" — the workflow only reads. Setting
`persist-credentials: false` prevents the token from being on disk
where a subsequent step (or a malicious dependency) could read it.

This is a GitHub-recommended hardening, not a functional change.

### `paths-ignore`

The workflow previously ran on every push, including doc-only commits
(`**/*.md`, `docs/**`, `.gitignore`). A push that only changes docs
cannot contain new Kotlin or Java vulnerabilities — skipping it saves
CI minutes.

The ignore list mirrors the same list used in `ci.yml` for consistency.

### `concurrency.cancel-in-progress`

When a push to the same branch triggers a re-run, the previous run is
cancelled. This avoids the "stale SARIF upload from an abandoned run"
failure mode. Master and main branches keep running to completion (so
scheduled runs aren't cancelled by a fast-forward push).

### `permissions:`

Minimal permissions block. CodeQL needs:

- `actions: read` — to download CodeQL bundles
- `contents: read` — to checkout the repo
- `security-events: write` — to upload SARIF results to the Security tab

`packages:`, `pages:`, `id-token:`, etc. are intentionally NOT granted.

## What CodeQL catches on this codebase

CodeQL's `java-kotlin` extractor, with `security-extended`, currently
flags:

- SQL injection patterns (Room raw queries without parameter binding)
- WebView configuration issues (`setJavaScriptEnabled(true)` without
  safe file access)
- Insecure cryptography (`Cipher.getInstance("DES")`, hardcoded keys)
- Path traversal in file I/O
- Deserialization of untrusted data
- Android `exported` attribute misconfigurations

Findings appear in the repository's Security tab as alerts, and as
inline PR comments when introduced in the PR.

## What CodeQL does NOT catch

- Compose UI correctness (use `compose-ui-test` + screenshot tests)
- Detekt lint rules (use the `static-analysis` job in `ci.yml`)
- Build configuration issues (Gradle, R8 rules — use `lintDebug`)
- Resource leaks (use LeakCanary on a debug build)

## Tuning

Common tweaks and where they go:

| Goal | Change |
|---|---|
| Stricter security review | Override `queries:` to `security-and-quality` on a per-run basis |
| Faster builds | Drop `--no-build-cache` only if you've already green-lighted the current PR |
| Different branch | Add to `branches:` list in both `push:` and `pull_request:` triggers |
| Disable scheduled run | Remove the `schedule:` block |
| Run on Dependabot PRs | Add `pull_request_target:` trigger (security: read [the docs](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/automating-dependabot-with-github-actions) first) |
