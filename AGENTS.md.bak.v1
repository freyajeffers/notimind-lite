# 🤖 AGENTS.md: NotiMind Lite Implementation Agent

This document defines the role, context, and operating procedures for AI agents tasked with the recreation, maintenance, or extension of the **NotiMind Lite** project.

## 🎯 Project Mission
**NotiMind Lite** is a privacy-focused Android application designed to capture, log, and recover system notifications. It provides a high-utility "notification insurance" policy, allowing users to retrieve dismissed notifications through advanced semantic search and cloud synchronization.

## 🏗️ Technical Architecture
The project is built on a modern Android stack:
- **UI Layer**: Jetpack Compose with Material Design 3 (Dynamic Theming).
- **Persistence**: Room SQLite Database with FTS4 for keyword search and Vector embeddings for semantic discovery.
- **Background Services**: `NotificationListenerService` for real-time capture and `WorkManager` for background synchronization.
- **Cloud Layer**: Firebase Auth (Google Sign-In) and Firestore for user-scoped bidirectional sync.
- **Reliability**: Direct Boot support for early-device recovery.

## 🛠️ Agent Toolset & Reference Materials
When working on this project, the agent MUST reference the following artifacts located in `/home/freya/antigravity/NotiMind-Lite/chat-extract/`:

1.  **`summary.md` (Technical Implementation Guide)**: The authoritative source for "How" to implement specific features. Contains the literal diffs and phased requirements.
2.  **`ideal_commits.txt` (Commit Sequence)**: The definitive order of operations. Every change should be applied as an atomic commit as defined here.
3.  **`ideal_changelog.md` (Release History)**: The high-level roadmap and versioning milestones.

## 🚦 Operating Principles
- **Linearity**: Never skip a phase. The project has a strict dependency graph (Build $\rightarrow$ Data $\rightarrow$ Service $\rightarrow$ UI $\rightarrow$ Sync $\rightarrow$ Perf).
- **Verification**: Every phase must be verified via `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.
- **Atomic Application**: Apply one commit at a time. Do not bundle multiple `ideal_commits.txt` entries into a single change.
- **Namespace Integrity**: Do not modify the package names, `applicationId`, or namespace in `build.gradle.kts` or `AndroidManifest.xml` unless explicitly directed.
- **Direct Boot Awareness**: Any component interacting with the database before user unlock must be marked `android:directBootAware="true"`.

## 🏁 Definition of Done
A feature is "Done" when:
1. The literal code from `summary.md` is applied.
2. The commit from `ideal_commits.txt` is registered.
3. The build succeeds.
4. The associated unit/Robolectric tests pass.
