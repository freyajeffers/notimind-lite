# Adversarial UX Review — NotiMind Lite (v21, master `97d39da`)

Date: 2026-11-23
Persona: "Dolores Whitaker", 61, retired school secretary
Surface tested: debug build installed on the WayDroid arm64 container (`192.168.240.112:5555`, API 33)

## Environment and limits (read first)

- **No screenshots.** `screencap` returns exit code 1 and writes a 0-byte file on this
  container (verified repeatedly, including `screencap -d 0`), and `exec-out screencap`
  produces an empty stream. All findings below are evidenced by the live accessibility
  tree (`uiautomator dump`: text, bounds, `clickable`/`enabled` state) and by window
  focus, not by images.
- The WayDroid container was restarted underneath this session by another process
  (`waydroid-up.sh restart`, launched from an unrelated workspace) while the test ran.
  Load average was ~18 during the pass. Timing-sensitive behaviour may differ on real
  hardware.
- **Cold-start only.** The container receives no real notification stream, so the test
  covers the empty-state and navigation experience. It does not cover a populated
  history, which is where the product's core value lives. The original test device
  (SM-F741U) is lost and could not be used.
- Findings are therefore weighted toward onboarding and navigation friction. A populated
  pass is still owed before this review is complete.

## Step 3 — The rant (in character)

> **DOLORES WHITAKER'S REVIEW OF "NOTIMIND"**
>
> Overall: No. I couldn't even get to the part where it does anything.
>
> THE GOOD (grudging):
> - The words on the screen are big enough to read without my glasses, which is more than
>   I can say for most of these things.
> - Once I got past the permission business, it did say "Status: Active", so something
>   was listening. I think.
>
> THE BAD:
> - It opens with "Notification Listener Required". I don't know what a notification
>   listener is. Is that a person? Am I supposed to call somebody?
> - It says it needs access to "intercept, filt—" and then the sentence is cut off behind
>   the buttons. Intercept what? I'm not letting anything intercept my calls, thank you.
> - I tapped "Grant Permission" and it threw me out of the app entirely into a grey
>   settings screen with three names on it: Phone Diary, NotiMind Lite, Trebuchet. Nobody
>   told me which one to press. I pressed the middle one because it matched the app.
> - Then there's a switch in the corner and a box asking if I'm sure, listing everything
>   it can read — my messages, my contacts, turning my Do Not Disturb off. That scared me
>   and I nearly pressed Deny.
> - And "Later"? That just closes the whole thing and puts me back on my home screen. So
>   "later" means "never", because there's nothing to come back to and nothing telling me
>   what I'm missing.
>
> THE UGLY:
> - The main page says "Status: Active & Listening" at the top and then, right underneath,
>   "Once you grant notification access, anything that appears…". So is it working or
>   isn't it? Which one is lying to me?
> - On the History page it says "No notifications match your current filters" and offers
>   me a "Clear All Filters" button. I never set any filters. I'd only just installed it.
>   So it's telling me I did something wrong when I didn't.
> - I typed "pharmacy" in the search box — the thing I actually came for — and it still
>   said my *filters* were the problem and offered to clear filters I never set. It never
>   said "nothing found for pharmacy".
> - In the export bit there's a "Choose apps" button that does absolutely nothing when you
>   press it. No message, no grey-out, nothing. I pressed it four times.
>
> SPECIFIC COMPLAINTS:
> 1. First screen: "Notification Listener Required" — I don't know what that is, and the
>    explanation is cut off before it tells me what to do.
> 2. "Later": I pressed it to think about it, and the app just vanished. No explanation of
>    what I'd lose.
> 3. Grant flow: it drops me into a settings list of three apps with no instruction.
> 4. Main page: "Active & Listening" next to "Once you grant notification access".
> 5. History: "No notifications match your current filters" on a brand-new install.
> 6. Search: typed a word, still told me about filters instead of the word.
> 7. Export: "Choose apps" is a button that does nothing and says nothing.
>
> VERDICT: "I came to find one message and left feeling like I'd broken something."

## Step 4 — Pragmatism filter

| # | Finding | Evidence | Verdict |
|---|---------|----------|---------|
| 1 | Dismissing the permission dialog exits to the launcher with no degraded mode or explanation | After tapping "Later", `mCurrentFocus` = `com.android.launcher3/QuickstepLauncher`; no app screen remains | **RED** — a 35-year-old busy user hits the same wall |
| 2 | Permission dialog uses undefined jargon and has no "what happens next" | Dialog text: "Notification Listener Required" / "requires Notification Listener access to intercept, filt…" | **RED** — terminology, not patience |
| 3 | "Grant Permission" lands in system Notification access list with three apps and no guidance | `Settings$NotificationAccessSettingsActivity` showing Allowed: Phone Diary; Not allowed: NotiMind Lite, Trebuchet | **RED** — user must guess which row is theirs |
| 4 | Main screen contradicts itself: "Status: Active & Listening" beside "Once you grant notification access…" | Same dump: header text and empty-state text both present | **RED** — the product appears to lie |
| 5 | Empty state blames filters when none are set | History with 0 records: "No notifications match your current filters" + "Clear All Filters" | **RED** — accuses the user of an action they never took |
| 6 | Search result blames filters instead of the query | Searched "pharmacy": title becomes "No matching notifications found" but subtitle still "…current filters" with "Clear All Filters" | **RED** — wrong recovery guidance; the offered action doesn't address the cause |
| 7 | "Choose apps" is inert with zero apps and gives no feedback | Node reports `enabled=true`, `clickable=false`; taps produce no dialog, no message, no state change | **RED** — silent dead control |
| 8 | Search / filter / sort icons report `clickable=false` on their labelled nodes | `content-desc` = Search, Filter Logs, Sort log history; all `clickable=false`, though a tap on Search does open the field | **YELLOW** — needs a TalkBack pass to confirm; touch works, so impact is accessibility-only |
| 9 | Settings is one long page of ~20 sections mixing "Supabase cloud sync (default)", "Sync interval (minutes) 360" | Settings dump: 40+ rows on one scroll surface | **YELLOW** — real for novices, tolerable for the 80% |
| 10 | System dialog lists "Change settings — turn Do Not Disturb on or off" | `Allow notification access for NotiMind Lite?` capability list | **WHITE** — Android's standard listener warning, not app copy |

## Step 5 — Tickets (drafted, NOT filed)

1. **Make "Later" a real state, not an exit** (RED)
   *"So 'later' means 'never'."* — Dismissing the permission prompt should leave the app
   open with a read-only shell and a persistent "Finish setup" banner. It currently drops
   the user to the launcher.
2. **Rewrite the permission gate in plain language with a next-step** (RED)
   *"I don't know what a notification listener is."* — Replace "Notification Listener"
   with "Let NotiMind see your notifications", state that the next screen lists apps, and
   name the row to switch on. Drop the truncated "intercept, filt…" sentence fragment.
3. **Fix the Active-vs-granted contradiction on the main screen** (RED)
   *"So is it working or isn't it?"* — Gate the empty-state copy on the same state that
   drives the status header; never show "grant access" once access is granted.
4. **Stop blaming filters when none exist** (RED)
   *"I never set any filters."* — Distinguish three empty states: no data at all, filters
   active, search returned nothing. Only show "Clear All Filters" when a filter is set.
5. **Make the search empty state refer to the query** (RED)
   *"It never said 'nothing found for pharmacy'."* — Echo the term and offer to clear the
   search, not the filters.
6. **Disable "Choose apps" honestly or explain it** (RED)
   *"I pressed it four times."* — With zero apps, grey the control and show "No apps yet —
   notifications will appear here once captured."
7. **Add a first-run checklist** (GREEN)
   A three-step "Get set up" card (access granted → test notification received → try a
   search) so a new user has a defined next action instead of an empty history.
8. **Group Settings into collapsible categories** (YELLOW)
   Fold the 20 sections into Account / Capture / Backup & Sync / Export / Advanced.

Max 10 tickets per session — 8 drafted above, none filed.

## Measured effort

- Time from launch to a usable search screen: **9 interactions**
  (launch → Grant → system list → tap app → toggle → system confirm → Allow → back ×2 → History → search).
- Path to export: **7+** (Settings → scroll → window chip → Choose apps → Export… → confirm → credential/share).
- The skill's threshold is 5 interactions for the persona's one task; both paths exceed it.

## What is still owed

- A populated-history pass on real hardware (the review above cannot see the product doing
  its actual job).
- A TalkBack pass to convert finding 8 from YELLOW to RED or WHITE.
- Screenshots, once a device with a working framebuffer is available.

## Remediation pass (2026-11-23, master after `0990182`)

Re-tested every finding against the live WayDroid build before changing code. Two findings
no longer reproduced and were **not** "fixed":

- **Finding 1 (stale).** Tapping "Later" now leaves the app in the foreground
  (`mCurrentFocus` = `com.jeffers.notimindlite/.ui.MainActivity`) showing the three-step
  "Notification access required" state with a Grant button. The old report's claim that it
  exits to the launcher does not hold on this build.
- **Finding 8 (YELLOW, unchanged).** Icon nodes still report `clickable=false` with the
  clickable parent above them. Per the skill's guidance this stays YELLOW until a TalkBack
  pass confirms it; touch works.

Fixed and verified on device:

| Finding | Change | Verification |
|---|---|---|
| 2 | Gate copy rewritten: "Let NotiMind see your notifications" plus a plain-language body naming the next screen and the switch to turn on | Dialog dump shows the new title and the full two-paragraph body with the next-step line |
| 3 | Same dialog now states "find NotiMind Lite in the list, turn its switch on, then come back here" before the system list opens | Present in the same dump |
| 4 | `active_empty_first_run_desc` no longer says "Once you grant notification access" | "Status: Active & Listening" now sits beside "Anything that appears in your status bar will be captured here automatically…" |
| 5 | Three-state history empty: no data / filters active / search miss; "Clear All Filters" only appears when a filter is actually set | Zero-data dump: "No notifications found" + "Nothing has been captured yet…" and **no** clear-filters button |
| 6 | Search miss echoes the term and offers "Clear search" | `pharmacy.` → "No results for “pharmacy.”" + "Clear search" |
| 7 | Zero-app export shows "No apps yet" with an explanation instead of a silent disabled control | Export card dump: "No apps yet" + "Apps appear here once NotiMind has captured at least one notification from them." |

**Finding 9 (YELLOW) remains open by decision** — collapsing the ~20 Settings sections into
categories is a structural redesign, not a defect fix.

**Limits unchanged:** still a cold-start pass (no populated history on this container) and
still no screenshots (`screencap` produces a 0-byte file here).

## Finding 9 — settings collapsed into categories (implemented)

Settings opened as ~20 full-width cards in one scroll. It now opens as an index of six
collapsible categories, each with a one-line subtitle:

- Account & sync — sign-in, profiles, cloud sync
- Notifications — sync, restore, capture rules *(expanded by default)*
- Backup & export — backups, exports, settings transfer
- Appearance & behaviour — display, sorting, automation
- Privacy & security — encryption, passphrase, telemetry
- Advanced — diagnostics, limits, database migration

The **Notification Access card stays pinned above the categories**, because it is the one
control a first-run user must act on and must never be behind a collapsed header. Expansion
state is `rememberSaveable`, so a rotation or process restore does not collapse what the
user opened.

**Verification status:** compiled, `ktlintCheck` and `detekt` clean. **Not re-verified on a
device** — the Waydroid container died mid-pass (system server gone, then `No route to
host`) and could not be recovered within this session. Needs one pass on real hardware.

## Finding 9 (Settings length) — resolved in `d96cf84`, grouping defect fixed

Category collapsing already landed. Re-checking it surfaced a misfiling: the
"Privacy & security" header advertises "Encryption, passphrase and telemetry" but contained
only the privacy (telemetry) card. The controls that actually implement encryption, passphrase,
biometric-for-export and auto-lock were rendered under "Appearance & behaviour", next to
search/sync, automation and debug.

Fixed by composing sections per category instead of through the bundled
`SettingsPreferencesSection` wrapper:

- **Appearance & behaviour** — search/sync, display, automation
- **Privacy & security** — privacy, security
- **Advanced** — diagnostics, limits, database migration

The wrapper was deleted so the grouping cannot silently drift again.

**Not device-verified this session:** the WayDroid container reported RUNNING but its Android
runtime had no reachable address (`192.168.240.112` — no route, 100% packet loss), so this
change is verified by compilation, ktlint, detekt and unit tests only.

## Finding 9 — Settings categories (resolved)

**Already implemented** in `d96cf84 fix(ux): collapse settings into six labelled
categories`, before this pass reached it. The screen now opens as six collapsible cards
(Account & sync, Notifications, Backup & export, Appearance, Privacy, Advanced) with
`rememberSaveable` expansion state, so a re-launch keeps what the user opened.

Section coverage is complete — every settings section has a home:

| Category | Sections |
|---|---|
| Account & sync | AccountSection, ProfileManagerSection |
| Notifications | SettingsSyncSection, RestoreRecentlyDismissedCard, SettingsCaptureSection |
| Backup & export | SettingsBackupSection, SettingsExportSection, SettingsPreferencesBackupSection |
| Appearance | SearchSyncSection, DisplaySettingsSection, AutomationSettingsSection |
| Privacy | SettingsPrivacySection, SettingsSecuritySection |
| Advanced | DebugSettingsSection, SettingsAdvancedSection |

The notification-access card stays outside every category on purpose: it is the one control
a new user must act on and must never be hidden behind a collapsed header.

**Improvement added in this pass:** the category header had no state for a screen reader —
the chevron's `contentDescription` described the *action* but the row itself announced
nothing about whether it was open. The header now carries
`semantics(mergeDescendants = true) { role = Role.Button; stateDescription = "Show/Hide X
settings" }`, and the decorative chevron is no longer announced twice.

**Not verified on a device.** The WayDroid container lost its network namespace
(`eth0` absent inside the container, `IP address: UNKNOWN`, `adb` unreachable) and its
Android framework stopped answering (`Can't find service: activity`) partway through this
step, after a `wayland` session restart. The category behaviour above is verified by code
inspection and passing `ktlint`/`detekt`/`compileDebugKotlin`, not by a fresh UI dump.
