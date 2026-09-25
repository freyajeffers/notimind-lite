# Stale Documentation Reconciliation

Generated from the repository state on 2026-09-24.

## Precedence

1. Current source, tests, generated Room schemas, and Git state.
2. `AGENTS.md` and the newest `CHANGELOG.md`.
3. Newer audit and implementation documents under `docs/`.
4. Older architecture, roadmap, installation, and user-facing documents.

## Findings

| Document claim | Current repository truth | Resolution |
|---|---|---|
| Room schema v16/v18 | `AppDatabase` is version 19; schemas `18.json` and `19.json` exist. | Update authoritative architecture references to v19. |
| SQLCipher-backed Room | No `sqlcipher` or `net.zetetic` dependency/source was present before this work. | Implemented in the current phase; migration remains explicit and non-destructive. |
| Offline-only/no network permission | Manifest declares `INTERNET` and `ACCESS_NETWORK_STATE`; Firebase Auth/Firestore are active. | Treat cloud-sync documentation as current; offline-only claims are stale. |
| No Firebase/cloud SDK | `AuthManager`, `FirestoreSyncRepository`, `SyncWorker`, and Firebase initialization are present. | Superseded by current source. |
| Destructive Room fallback allowed | Root agent rules prohibit destructive migration fallback. | Do not use `fallbackToDestructiveMigration`. |
| QUERY_ALL_PACKAGES must be removed | Root `AGENTS.md` explicitly requires preserving it absent explicit user direction. | Preserve for now. |
| QUICKBOOT_POWERON should be removed | Still declared in the current manifest. | Documented as an outstanding hardening item. |
| Cloud/account deletion is available | `purgeUserData` rejects deletion; local Clear Log is separate from Firestore. | `docs/policy/room-delete-policy.md` is authoritative. |
| Full SQLCipher migration plan | Earlier documents describe an aspirational migration. | This work adds the encrypted database factory and explicit key storage; legacy plaintext database migration remains a required follow-up before shipping to existing installs. |

## Current baseline

- Branch: `master`, clean before this work, tracking `origin/master`.
- HEAD before this work: `0f36a7d`.
- Build gates previously verified: `:app:assembleDebug` and `:app:testDebugUnitTest`.
- Production namespace/application ID: `com.jeffers.notimindlite`.
- Database version: Room v19.
- Current cloud behavior: Firebase Auth and Firestore synchronization enabled.
- Current security gap: SQLCipher was not previously wired into Room; plaintext Room files were possible.

## Remaining documentation work

Older documents still contain historical or aspirational statements. They should be updated incrementally when their associated implementation is changed; this report prevents those statements from being mistaken for current behavior.
