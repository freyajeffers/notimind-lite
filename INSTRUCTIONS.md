# 📜 INSTRUCTIONS.md: Project Recreation Roadmap

This document provides the high-level orchestration instructions for recreating the **NotiMind Lite** project from a baseline state.

## 🚀 Quick Start
To begin the recreation process:
1.  **Load Context**: Read `AGENTS.md` to understand the architecture and operating principles.
2.  **Reference Technicals**: Open `/home/freya/antigravity/NotiMind-Lite/chat-extract/summary.md` for the literal diffs and technical requirements.
3.  **Follow Sequence**: Execute the commits exactly as listed in `/home/freya/antigravity/NotiMind-Lite/chat-extract/ideal_commits.txt`.

---

## 📅 Implementation Roadmap

### Phase 1: Project Foundation (`v0.1.0`)
**Focus**: Build environment, Version Catalogs, and CI/CD.
- **Commits**: `[commit_001]`
- **Key Task**: Setup `libs.versions.toml` and Gradle optimizations.
- **Verification**: `./gradlew assembleDebug`

### Phase 2: Reliability & Recovery (`v0.2.0`)
**Focus**: Direct Boot, Database Migrations, and Boot Restoration.
- **Commits**: `[commit_005]`, `[commit_006]`, `[commit_007]`, `[commit_008]`
- **Key Task**: Implement `BootReceiver` and `DatabaseMigrator`.
- **Verification**: Verify app starts after simulated reboot.

### Phase 3: Discovery & Intelligence (`v0.3.0`)
**Focus**: FTS4, Vector Search, and Hybrid Ranking.
- **Commits**: `[commit_009]`, `[commit_010]`, `[commit_011]`, `[commit_012]`, `[commit_013]`
- **Key Task**: Implement `HybridSearchEngine` and `DynamicClusterManager`.
- **Verification**: Run semantic search unit tests.

### Phase 4: Experience & Material Design (`v0.4.0`)
**Focus**: Material 3, Navigation, and Actionable UI.
- **Commits**: `[commit_014]`, `[commit_015]`, `[commit_016]`, `[commit_017]`
- **Key Task**: Implement `ActionableChips` and `SpeedDialSettingsFab`.
- **Verification**: Manual UI check of the Active Notifications screen.

### Phase 5: Cloud Ecosystem (`v1.0.0`)
**Focus**: Google Auth, Firestore Sync, and Gemini Maps.
- **Commits**: `[commit_018]`, `[commit_019]`, `[commit_020]`, `[commit_021]`
- **Key Task**: Implement `AuthManager` and `FirestoreSyncRepository`.
- **Verification**: Successful Google Sign-In and data sync.

### Phase 6: Stability & Advanced Utilities (`v1.1.0`)
**Focus**: Memory optimization, Snooze Reminders, and Intent Dispatch.
- **Commits**: `[commit_022]`, `[commit_023]`, `[commit_024]`, `[commit_025]`, `[commit_026]`
- **Key Task**: Implement `AppIconCache` and `NotificationLauncher`.
- **Verification**: Run memory profiler and test `SnoozeReminderReceiver`.

---

## 🛠️ Verification Checklist
- [ ] **Build**: `./gradlew assembleDebug` succeeds without warnings.
- [ ] **Tests**: `./gradlew testDebugUnitTest` all pass.
- [ ] **Direct Boot**: Database is accessible before user unlock.
- [ ] **Cloud**: User data is isolated by `userId` in Firestore.
- [ ] **Memory**: `AppIconCache` is cleared on `onTrimMemory`.
- [ ] **Capture**: `NotificationListenerService` captures notifications in real-time.
