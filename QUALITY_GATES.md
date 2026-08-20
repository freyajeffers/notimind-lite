# Quality Gate Checklist for NotiMind Lite

This document defines the mandatory quality gates that must be passed before any code is merged into the `master` branch.

## 1. Automated Gates (CI)
All of the following GitHub Actions must pass:
- [ ] **Detekt**: No new code-smells or style violations.
- [ ] **Android Lint**: No errors (`abortOnError = true`).
- [ ] **Unit Tests**: All tests in `testDebugUnitTest` must pass.
- [ ] **Build**: `assembleRelease` must compile without errors.
- [ ] **Coverage**: Test coverage report must be generated and reviewed for regression.

## 2. Manual Verification
- [ ] **Code Review**: At least one approved review from a senior maintainer.
- [ ] **APK Smoke Test**: The release APK must be installed and launched on a physical device/emulator without crashing.
- [ ] **Reflection Check**: Verify that Room database and Firebase services function correctly in the minified release build.

## 3. Versioning & Release
- [ ] **Versioning**: `versionCode` must be incremented for every release build.
- [ ] **Signing**: Ensure the build is signed with the correct production keystore (not the debug keystore).
