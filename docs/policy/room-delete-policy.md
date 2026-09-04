# Data Retention Policy — ROOM_DELETE_POLICY

This document is the authoritative source for the "deletion is permanently
disabled" claim made in
[`FirestoreSyncRepository.purgeUserData`](../app/src/main/java/com/jeffers/notimindlite/data/sync/FirestoreSyncRepository.kt).

If you are here because the codebase references this doc from an exception,
see **Enforcement Layers** below for what is and is not actually enforced by
the code versus by external configuration.

## Policy

User notifications logged by NotiMind Lite, both locally (Room SQLite) and
remotely (Firestore), are **never deleted by the application**. The only
write operations permitted against these stores are INSERT and UPDATE.

This is a product decision, not a technical limitation. It exists for two
reasons:

1. **Reliability**: NotiMind Lite's core promise is "notification insurance."
   A user who dismissed a notification must be able to recover it from the
   history log indefinitely. Silent deletion would break that promise.
2. **Audit**: The notification log is the user's authoritative record of
   what they were notified about. Removing entries from the log retroactively
   would defeat the purpose of capturing them.

## Enforcement Layers

The policy is enforced by **three independent layers**. Any one of them
catching an unauthorized delete is sufficient; all three must be in place
for defense in depth.

### Layer 1: Client-side gatekeeper (in code)

[`FirestoreSyncRepository.purgeUserData`](../app/src/main/java/com/jeffers/notimindlite/data/sync/FirestoreSyncRepository.kt)
is the single client-side entry point for any caller that wants to remove
user data. It rejects every call with
`Result.failure(UnsupportedOperationException)` and logs a warning.

This layer catches every **in-app caller** that routes through the repository.
It does NOT prevent:
- Code that calls `firestore.collection(...).document(...).delete()` directly
  (bypassing the repository). There is no in-tree caller that does this; a
  future feature MUST route through `purgeUserData`.
- Code that calls `dao.clearAll()` or similar on Room DAOs. Currently used
  only for user-initiated "clear log" actions, which are scoped to local
  cache and do not delete Firestore.

### Layer 2: Firestore Security Rules (in the Firebase project, NOT this repo)

Firestore Security Rules on the `users/{uid}` document and its
`notifications` subcollection MUST deny `delete` operations. This is the
authoritative enforcement layer for cloud data. The rules live in the
Firebase project console, not in this repository, so they are not version-
controlled here.

**This is a known gap.** There is no automated test that verifies the
Firestore rules are configured to deny deletion. To audit the current
rules, open the Firebase Console > Firestore > Rules and confirm there is
no `allow delete` grant on `users/{uid}` or `users/{uid}/notifications`.

### Layer 3: Room schema (no DELETE triggers)

The Room `@Database` declaration does not include any DELETE triggers. The
only DELETE statements in the codebase are:
- `dao.clearAll()` — clears local `notifications` table. Used by the
  "Clear Log" Settings action.
- `dao.delete...` — none currently in production code.

Neither operation removes the persistence store itself or affects Firestore.

## What "Clear Log" Does (and Does Not Do)

The Settings screen exposes a "Clear Log" action. When invoked:
- Local Room `notifications` table is emptied (rows deleted).
- Firestore `users/{uid}/notifications` collection is **NOT** touched.

This is the only deletion operation in the app, and it is explicitly
opt-in by the user. It does not violate the policy because:
- The persistence store remains (empty table, not dropped table).
- The cloud record remains (the user's Firestore copy is unaffected).
- The next notification will re-create a local row.

## Future Considerations

If a future feature legitimately needs to remove data — for example, a
GDPR right-to-erasure request, a "reset account" feature, or a sync-conflict
resolution that requires dropping stale records — the following steps MUST
happen before implementation:

1. **Product review**: confirm the request is compatible with the
   notification-insurance promise. If not, deny the request at the product
   level.
2. **Policy update**: this document MUST be amended with the new scope and
   the permitted operation surface.
3. **Layer 2 update**: Firestore Security Rules MUST be reviewed and
   amended to allow the new operation.
4. **Layer 1 update**: `purgeUserData` MUST be either extended to permit
   the new operation or replaced with a more granular policy object.
5. **Test additions**: add boundary tests in `tier2_boundary/` that
   exercise the new path and confirm it is the ONLY path that removes data.

Do NOT silently relax the client-side gatekeeper. The exception message in
`purgeUserData` references this document; changing the behavior without
updating both is a policy violation.

## Related Code

- [`FirestoreSyncRepository.kt`](../app/src/main/java/com/jeffers/notimindlite/data/sync/FirestoreSyncRepository.kt) — Layer 1
- [`SettingsScreen.kt`](../app/src/main/java/com/jeffers/notimindlite/ui/screens/SettingsScreen.kt) — "Clear Log" entry point
- [`NotificationDao.kt`](../app/src/main/java/com/jeffers/notimindlite/data/local/NotificationDao.kt) — `clearAll` and related
- [Audit 2026-09-02 F-D](../audit/2026-09-02-integration-pass.md) — finding that surfaced this policy doc
