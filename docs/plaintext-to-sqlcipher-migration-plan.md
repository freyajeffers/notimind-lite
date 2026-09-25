# Plaintext-to-SQLCipher Migration Plan

## Scope

`AppDatabase` currently opens DE and CE databases through SQLCipher for new/opened database paths. This plan covers existing installations whose Room files were created as plaintext SQLite databases. It does not claim that legacy files are migrated until the implementation and on-device validation below are complete.

## Safety invariants

1. Never delete the only readable copy before encrypted verification succeeds.
2. Never expose SQLCipher keys in logs, telemetry, files, exceptions, or test output.
3. Never migrate while an uncontrolled writer can modify the source database.
4. Preserve Room schema semantics and run the existing explicit migrations; never use destructive migration fallback.
5. A crash or force-stop must leave either the original source intact or a resumable migration state.
6. CE and DE databases are independent migration units with independent keys and completion markers.

## Proposed state machine

`NOT_REQUIRED → PREFLIGHT → COPYING → VERIFYING → CUTOVER_PENDING → COMPLETE`

Failure states are `RETRYABLE_FAILURE` and `ROLLBACK_REQUIRED`. State is stored in device-protected metadata without storing key material. Transitions are monotonic except for an explicit rollback to the preserved source.

## Phase 1: preflight

- Detect the expected plaintext database, `-wal`, and `-shm` files for each DE/CE database.
- Confirm the app process owns the files and that no migration is already active.
- Check available storage against source size plus encrypted target, temporary files, and safety margin; begin with `2.5 × source_size` as the conservative threshold.
- For CE migration, require `UserManager.isUserUnlocked`; schedule rather than attempt pre-unlock.
- For DE migration, coordinate with Direct Boot components and run only when the database is quiescent.
- Checkpoint/close the source Room instance before copying and record schema version, file sizes, and row counts in a non-secret audit record.

## Phase 2: encrypted target creation

- Obtain the per-database key through `SqlCipherKeyManager` and `EncryptedDatabaseFactory`; do not derive keys from user-visible text.
- Keep DE and CE keys separate. Use StrongBox-backed Android Keystore protection when available, with a tested Keystore fallback when unavailable.
- Create the target in an app-private temporary path with restrictive file permissions.
- Open it through the production Room builder and explicit migrations, not a separate untested schema.

## Phase 3: bounded copy

- Stop application writes for the source database and hold an application-level migration lock.
- Copy tables in bounded batches (recommended starting range: 500–5,000 rows), ordered by stable primary key.
- Use streaming cursors; never load a complete table or database into memory.
- Preserve nullability, timestamps, keys, dismissal state, tags, and all schema columns, including FTS rows through the supported DAO/database path.
- Commit each batch transactionally and persist a checkpoint containing table and last copied key.
- If a source schema is older, migrate the source to the supported logical schema before copying, using registered Room migrations only.

## Phase 4: verification and cutover

Verify before replacing any path:

- Room can open the target with the production SQLCipher factory.
- Per-table row counts match the source snapshot.
- Primary-key sets and deterministic per-row digests match, excluding intentionally regenerated metadata.
- FTS queries and representative DAO reads return equivalent results.
- The target survives close/reopen with the same key.
- The target cannot be opened as ordinary SQLite without the key.

After verification, atomically switch the database path/marker to the encrypted target, reopen through `AppDatabase`, and run startup plus DAO smoke checks. Keep the source in a quarantine path until the configured post-cutover retention period expires and a second verification succeeds.

## Rollback

- If preflight or copying fails, close the target and retain the source; mark the operation retryable.
- If verification fails, do not cut over. Preserve both files for diagnostics and delete only the incomplete target after the failure record is durable.
- If post-cutover startup fails, close the encrypted target, restore the source marker/path, reopen the source, and mark `ROLLBACK_REQUIRED` for operator review.
- Only delete the plaintext source after successful cutover, a later reopen verification, and the retention policy's approval. Record the deletion without recording keys or notification content.

## Interruption handling

Simulate force-stop, process death, reboot, low storage, battery removal, and concurrent notification delivery during every copy phase. On restart, inspect the checkpoint and file integrity. Resume only when the source snapshot remains valid; otherwise discard the partial target and restart from a fresh source snapshot.

## Device test matrix

| Dimension       | Required cases                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------------ |
| Device security | StrongBox-capable physical device; physical device without StrongBox; emulator Keystore fallback |
| Android         | API 33, 34, 35, and 36; at least one physical device                                             |
| Database size   | `<1 MB`, `10–50 MB`, `200–500 MB` stress case                                                    |
| Database type   | CE after unlock; DE across reboot and direct-boot boundary                                       |
| Runtime state   | Foreground idle; background WorkManager; notification burst/write pressure                       |
| Storage/power   | Adequate storage; low-storage abort; AC; low battery; interruption                               |
| Recovery        | Force-stop/process death; reboot; resume; verification failure; rollback                         |
| Data            | Empty DB; null/long text; FTS rows; dismissed/active rows; maximum timestamps and IDs            |

For every matrix cell assert: no data loss, matching counts/digests, successful Room reopen, FTS behavior, correct state transition, no key/content leakage in logs, and a valid rollback or resume result.

## Test layers

1. **JVM unit tests:** state machine, storage preflight, batch checkpointing, digest comparison, failure transitions.
2. **Robolectric tests:** Room source/target copy with small fixtures and migration lock behavior where SQLCipher native loading is supported.
3. **Instrumented tests:** real SQLCipher open/reopen, Keystore and StrongBox selection, file encryption, process interruption, and DE/CE behavior.
4. **Manual device acceptance:** `adb` log inspection, database file pull/check, reboot-before-unlock, and notification burst during migration.

## Exit criteria

- Small and medium databases pass the complete matrix on staging devices.
- Large-database stress completes within the agreed time/memory budget.
- Interrupted migrations resume or roll back without data loss.
- Plaintext files are not removed until post-cutover verification passes.
- `:app:assembleDebug` and `:app:testDebugUnitTest` pass with migration tests included.
- A release runbook documents recovery without requiring root or exposing secrets.
