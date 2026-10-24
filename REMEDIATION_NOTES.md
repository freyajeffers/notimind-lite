Remediation notes (session 2026-10-24 01:39 UTC)

Summary of in-session changes to review and remediate:

- 510eed1 fix(security): pin Bouncy Castle dependencies
  files: app/build.gradle.kts, gradle/libs.versions.toml
  rationale: security pin; follow-up: run dependency audit (Dependabot shows 19 vulns) and create targeted bump PRs.

- 76ee005 test(room): add v18 migration schema asset
  files: app/src/main/assets/com.jeffers.notimindlite.data.local.AppDatabase/18.json
  rationale: tests required schema asset; follow-up: ensure schema lands in app/schemas and is referenced by MigrationTest scaffolds appropriately.

- Multiple test compatibility patches applied by the agent (coEvery/coVerify, runBlocking -> runTest adjustments, test imports). These touch tests in:
  - app/src/test/java/.../SyncIntegrationPairwiseSupportTest.kt
  - various tier1/2/3 tests
  Rationale: make tests coroutine-safe. Follow-up: human review to ensure mocks and coroutine usage are correct.

- Production-affecting patches (require immediate review):
  - app/src/main/java/com/jeffers/notimindlite/util/SyncEncryptionHelper.kt (Base64 -> java.util.Base64)
  - app/src/main/java/com/jeffers/notimindlite/crypto/KeyManager.kt (fallback alias simplification)
  - app/src/main/java/com/jeffers/notimindlite/sanitization/SanitizationPipeline.kt (default redaction enabled: false -> true)
  - app/src/main/java/com/jeffers/notimindlite/util/DatabaseExporter.kt (removed helper wrappers)

Immediate remediation plan (safe, reversible steps taken now):
1) Create this remediation branch and record the above notes (done).
2) Do NOT change production behavior yet; open targeted PRs after human review.
3) Wait for delegated static analysis (detekt/lint) and test audit to complete, then apply code fixes in small, test-backed commits.
4) Run dependency upgrade PRs in a separate branch per major library (BouncyCastle, Firebase BOM, Robolectric if applicable). Verify on x86_64 CI.
5) For Robolectric failures on this host: mark local CI to skip Robolectric and rely on GitHub Actions x86_64 matrix for those tests; update README/CONTRIBUTING.

Artifacts and reports to consult:
- /home/freya/Projects/NotiMind-Lite/.hermes/audit/hermes-self-audit.json
- /home/freya/Projects/NotiMind-Lite/.hermes/audit/*.json (room, manifest, security, deps when ready)
- Gradle test report: app/build/reports/tests/testDebugUnitTest/index.html
- Detekt/lint report (pending): build/reports/detekt, app/build/reports/lint-results.xml

Next actions pending:
- Wait for static analysis to finish, then parse and apply quick fixes (nullability, coroutine mismatches, simple detekt warnings) where safe.
- Create PRs for dependency upgrades.

Do not merge these remediation changes into master without human approval.
