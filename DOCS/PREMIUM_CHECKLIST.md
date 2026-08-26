# PREMIUM CHECKLIST

What stands between Scrolla as it is today and Scrolla as a secure, reliable,
good-looking app someone would not be able to tell from a funded one.

Written 2026-08-24 by Person B from a full pass over the tree. Every item below
was verified against the source, not recalled — file and line references are
current as of `dac1744`. Where a claim is a count ("151 hardcoded strings"), the
count came from a grep, and the grep is worth re-running before you argue with
it.

**Ownership** follows `AGENTS.md` §2: A owns `service/`, `room/`, `device/`,
`widget/`; B owns `ui/`, `firestore/`, `auth/`; `model/` and the build/release
setup are edit-together. Tagged per item.

**Status key:** ☐ not started · ◐ in progress · ☑ done

---

## P0 — Blocks "shippable at all"

These are not polish. Each one is a thing that is either missing entirely or
silently disabled, and none of them show up in a successful build.

### P0.1 — Tests exist at all `[Both]` — ◐ **46 tests as of 2026-08-26**

**First test landed 2026-08-24:** `GroupStandingTest` — 10 tests, passing in both the debug and release variants. It covers `selfStanding()`, the pure function behind Home's group rank, and was written alongside that feature rather than after it. `app/src/test/kotlin/` now exists, so the next test costs nothing to add.

The original finding, for the record: there was **no `app/src/test/` and no `app/src/androidTest/` directory**.
Not a thin suite — zero files. Meanwhile `app/build.gradle.kts` already declares
`testInstrumentationRunner`, JUnit, Espresso, and the Compose test artifacts.
Every hook is wired and nothing hangs on them.

This is ranked first because it matches our actual bug history. The last four
sessions were: metres rendered with a km label, a chart anchored to the wrong
day, a null health row mapping to ACTIVE, a landmark shown for 10 cm of
scrolling. All four are pure-function logic bugs — the exact class of bug a unit
test catches instantly and a device test catches slowly or never.

- [x] **P0.1a** Create `app/src/test/kotlin/com/scrolla/`. *Done 2026-08-24.*
- [x] **P0.1b** `DistanceFormatterTest`. *Done 2026-08-26 — 14 tests. Flagged on
      day one as the cheapest high-value test in the repo and written last, which
      is its own small lesson. Covers the 999.5 m crossover from both sides,
      `formatDistance` unit agreement (the delta-chip bug), the sub-metre case
      below, and `nearestLandmark` returning the nearest entry however absurd —
      asserted as **contract**, so that if the ratio gate ever migrates out of
      `HomeViewModel` into the formatter, both tests fail rather than one
      silently passing.*
- [ ] **P0.1c** `InsightsViewModelTest.buildWeek` — the rolling 7-day window,
      with a fixed clock. Assert today is last, no day is `isFuture`, and
      yesterday is labelled "Yesterday". This is the bug we just fixed; it should
      not be able to come back.
- [ ] **P0.1d** `HomeViewModel.landmarkFor` ratio gate — 0.5×/2.0× boundaries.
- [ ] **P0.1e** Wire `./gradlew test` into whatever passes for CI, even if CI is
      "the thing you run before you push".

> `buildWeek` and `landmarkFor` are `private`. Make them `internal` with
> `@VisibleForTesting` rather than testing through the whole ViewModel —
> the ViewModels construct their own dependencies via default args, which makes
> them awkward to instantiate in a unit test. See P0.1f.

- [ ] **P0.1f** Decide whether the default-constructor-arg injection pattern
      survives contact with tests. `ScrollaGraph` is reachable from a unit test
      only if it has been `init`'d, which it hasn't in a JVM test. Either pass
      fakes explicitly at every test site, or move to a test-visible seam.
      *Side-stepped for `GroupStanding` (2026-08-24) by making the logic a pure
      function over UI state rather than a ViewModel method — worth copying
      wherever it fits, but it does not answer the question for the ViewModels
      themselves, and P0.1c/P0.1d still run straight into it.*

### P0.2 — The release build is undefended `[Both]`

`app/build.gradle.kts` currently has:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = false
    }
}
```

No shrinking, no obfuscation, no ProGuard rules file, no signing config,
`versionCode = 1`, `versionName = "1.0"`. There is no meaningful difference
between the debug and release APKs today.

- [ ] **P0.2a** `isMinifyEnabled = true` + `isShrinkResources = true` for release.
- [ ] **P0.2b** Add `proguard-rules.pro` and keep rules for the Room entities and
      any Firestore-serialised model class. **Test the minified build on a
      device** — R8 breaking reflective Firestore deserialisation is the classic
      way a release APK dies where the debug one worked.
- [ ] **P0.2c** Real signing config, keystore kept out of the repo
      (`.gitignore` already covers `*.jks`/`*.keystore` — verify before the first
      release build, not after).
- [ ] **P0.2d** A versioning rule. `versionCode` must increment per build handed
      to anyone, or you cannot tell which APK a bug report came from.

### P0.3 — Nothing reports crashes `[A — taken 2026-08-25]`

Firebase is already in the project. Crashlytics is not. When the app dies on
someone else's phone you find out because they mention it, or you don't.

**This stopped being hypothetical on 2026-08-25.** The accessibility service
crashed and the crash itself was unrecoverable: `logcat -b crash` had rotated past
it within 9.5 hours and dropbox held nothing. We know the service died only
because `dumpsys accessibility` still listed it under `Crashed services`. On any
device we do not physically hold, that information does not exist at all. A has
taken this.

- [ ] **P0.3a** Add Firebase Crashlytics.
- [ ] **P0.3b** Non-fatal reports on the paths that currently swallow errors — the
      `catch` blocks in `ScrollRepository` that log and return a safe default are
      correct for the UI and invisible to us.
- [ ] **P0.3c** Confirm no scroll content, package list, or user identity beyond
      the Firebase UID reaches a crash report. See P1.2.

### P0.4 — Delete account is a dead row `[B]` — ◐ **built 2026-08-25, rules not deployed**

`SettingsScreen.kt:300-304` renders a "Delete" row with `enabled = false`. It has
never done anything.

For an app that holds a Google identity and writes to Firestore this is a Play
requirement. For an app built on an AccessibilityService it is *the* trust
affordance — you cannot ask someone to let you observe every scroll they make
and then offer no exit.

- [x] **P0.4g** Abort deletion if any group fails to clear. *Found 2026-08-25
      reviewing P2.3: `deleteAllUserData()` returns the groups it could **not**
      clear, and the caller was discarding that list and deleting the account
      anyway. Since every rule is gated on `request.auth.uid`, dropping the
      credential would have made that leftover data permanently unreachable —
      nobody could see it, nobody could delete it, in the flow whose whole point
      is removal. Now the account is kept and the user is told. A kept account is
      recoverable; a stranded orphan is not.*
- [x] **P0.4a** Implement deletion. *`GroupRepository.deleteAllUserData()` +
      `AuthRepository.deleteAccount()`. **Order is load-bearing:** every rule is
      gated on `request.auth.uid`, so deleting the Firebase account first would
      strand the cloud data permanently — unreachable rows nobody can see or
      remove, in the one flow whose entire purpose is removal. Cloud first, then
      Room, then the credential.*
- [x] **P0.4f** Scrub the deleted user's name from any group record they hold.
      *`SETTINGS_DELETE_BODY` has always promised "your name in group history
      will be replaced with '[deleted]'" and nothing did it. Must run **before**
      leaving the group: `isRecordImprovement()` requires the caller be a member,
      so after `arrayRemove` the write is denied forever. The record value
      survives — the rule permits an equal `recordKm` — so the group keeps its
      history and loses only the name.*
- [◐] **P0.4b** Firestore rules for self-deletion. ***Reviewed and approved by A 2026-08-25 (REVIEW_LOG #5). Still needs publishing to Firebase — reviewed is not deployed, and the delete button fails at `isSelfLeave` on a real device until it is.***
      Two changes:
      `isSelfLeave()` (subset + exactly-one-shorter + caller absent, so a member
      cannot remove someone else and add an impostor while keeping the size
      arithmetic intact), and a split of the `dailyTotals` write rule. That
      second one was a latent blocker nobody had noticed: `allow write` covers
      deletes, but on a delete `request.resource` is null, so
      `request.resource.data.userId == request.auth.uid` could never pass and
      **deleting your own totals was impossible**. Account deletion was
      unimplementable until this was split into `create, update` and `delete`.*
- [x] **P0.4c** Wipe the local Room database on delete. *`clearAllTables()`
      between the cloud wipe and the credential drop. This is the most sensitive
      store in the app — every scroll, per app, per hour — and skipping it would
      mean "delete my account" deleted the account and none of the surveillance.*
- [x] **P0.4d** Confirmation dialogue with real consequences. *And it turned out
      the copy already existed — `SETTINGS_DELETE_TITLE`, `_BODY`, `_CONFIRM`,
      `_CANCEL`, `_IN_PROGRESS` and `_INPUT_HINT` ("Type DELETE to confirm") were
      all written months ago and never rendered. The original design was stronger
      than the plain dialog first drafted here, so it is now wired as intended:
      type-to-confirm, an enumerated body, and a dismiss button that says "Keep my
      account" rather than "Cancel", which is ambiguous about what is being
      cancelled. Another instance of the written-but-never-rendered problem — see
      P4.3b.*
- [x] **P0.4e** Local data export. *`DataExport` builds a CSV — per-day history
      oldest-first, plus today's per-app breakdown — shared through the same
      `ACTION_SEND` chooser the group code and weekly recap already use. Placed
      directly above Delete, because the moment someone is looking for the way
      out is the moment they might want to take their data with them.*
      *CSV over JSON: the likeliest thing anyone does with this is open it in a
      spreadsheet. App labels come from other apps' manifests and can contain
      commas and quotes, so they are quoted and escaped — an unquoted label
      silently shifts every later column, which a user would only notice long
      after trusting the file. 10 tests.*
      *Corrected 2026-08-26 after a real run: the first version shared the CSV
      through `EXTRA_TEXT`, so chat apps pasted the whole thing into a message
      body rather than attaching anything. It is now written to
      `cacheDir/exports` and shared as a `content://` URI through a FileProvider
      scoped to that one directory — not the whole cache, which also holds
      Firestore's local persistence.*

### P0.5 — The Play-policy question is unresolved and load-bearing `[Both]`

Two separate problems, one already flagged in the manifest and one not.

`QUERY_ALL_PACKAGES` is Play-restricted; the manifest comment says so and says
sideloading makes it fine today. Correct.

The unflagged one is bigger: **Google restricts `AccessibilityService` to genuine
accessibility purposes**, and measuring scroll distance is not one. This is not a
detail to discover during review.

- [ ] **P0.5a** Decide explicitly: does Scrolla ever go on Play, or is it
      permanently a sideloaded app among friends? Write the decision down. It
      changes what every other item on this list means.
- [ ] **P0.5b** If Play: replace `QUERY_ALL_PACKAGES` with a `<queries>` element
      and accept a shorter app-breakdown list.
- [ ] **P0.5c** If Play: a privacy policy, hosted, linked from Settings, and
      honest about what the accessibility service can technically see versus what
      Scrolla actually stores.
- [ ] **P0.5d** Either way, a privacy screen in-app. The two
      `..._PRIVACY` strings we already show are good and are not sufficient.

---

## P1 — Security and data hygiene

### P1.1 — Repo hygiene `[Both]`

- [ ] **P1.1a** `app/google-services.json` is tracked, while `.gitignore` has a
      `# Google Services` header with **nothing under it**. For a Firebase Android
      app this file is not really a secret, but the empty section means the
      decision was never actually made. Make it deliberately.
- [x] **P1.1b** `.gitignore` rewritten as clean UTF-8. *The mangled lines matched
      nothing. Verified afterwards that `*.hprof` and `*.logcat` still ignore the
      heap dumps and logcat sitting in the repo root.*

### P1.2 — What leaves the device `[B]`

The app's stated promise is that the per-app breakdown never leaves the phone.
Worth proving rather than asserting.

- [ ] **P1.2a** Audit every Firestore write against `DATA_CONTRACT.md`. Confirm
      `dailyTotals` carries only `userId`, `date`, `displayName`, `totalKm`,
      `updatedAt` — no package names, ever.
- [ ] **P1.2b** Confirm the same for anything Crashlytics would attach (P0.3c).
- [ ] **P1.2c** `allow list: if false` on `/groups` is safe by inspection but was
      never simulated — the Rules Playground offers no list simulation. Re-verify
      by attempting an actual client-side collection query and confirming denial.

---

## P2 — Reliability

### P2.1 — The second-account test `[Both]` — ◐ **mostly passed 2026-08-24**

**This was the single highest-value outstanding item in the project, and it
passed.** A friend installed the APK on a second device, signed in with their own
Google account, joined a group by code, and the Leaderboard rendered
**"2 of 2 synced today"**.

That one line is a live end-to-end proof of the entire social pipeline:
`joinGroup()`'s `arrayUnion` on `members` actually commits (so `memberCount` is
2), A's `triggerFirestoreSync()` wrote a `dailyTotals` document for *both* users,
`getGroupLeaderboard()` read both back, and the deployed security rules permitted
every step. From 2026-08-16 to 2026-08-23 that flow was silently impossible.

- [x] **P2.1a** Second Google account, second physical device.
- [x] **P2.1b** Create a group on device 1, share the code, join from device 2.
- [x] **P2.1c** Both devices scroll; confirm both appear on **both** leaderboards
      with the right numbers and the right ascending order. *Passed 2026-08-24.
      Ascending order observed with two differing values, and the Home-vs-
      leaderboard figure matched on the second device too — checked by its owner,
      which is the stronger form of the test since it runs a different uid against
      the same rules. This also closes sprint item S2.9.*
- [ ] **P2.1d** Confirm the group record (`recordKm`) updates and that the
      improvement-only rule behaves with two writers. **Cannot pass as written —
      see P2.6.**
- [ ] **P2.1e** Log it in `DEVICE_TEST_LOG.md`, including both devices in the
      Section 2 table. That log's release gate wants 3 devices and 2
      manufacturers; this is the first entry.

### P2.7 — Rename and leave a group `[B]` — ☑ *built 2026-08-26*

Raised on 2026-08-23 ("I couldn't go and edit the group if wanted") and blocked
ever since, because `allow update` permitted only `isSelfJoin()` and
`isRecordImprovement()` — a write to `groupName` was simply denied.

- [x] **P2.7a** `isGroupRename()` rule, any member, name-only, 1–40 chars.
      *Self-reviewed — see P4.4c.*
- [x] **P2.7b** Rename UI: overflow menu → dialog with a live character count.
      The rule enforces the same bound, so without the counter an over-long name
      fails silently at the server.
- [x] **P2.7c** Leave group. *The rule was already there for free — `isSelfLeave()`
      was written for account deletion and is exactly what leaving needs.*
- [x] **P2.7d** Leaving deletes that group's `dailyTotals` for the user.
      *Removing only the `members` entry would leave them on the leaderboard with
      a live number, which is not leaving. Shares `clearUserFromGroup()` with
      account deletion so the two cannot drift apart.*
- [ ] **P2.7e** Untested on device. Needs the rules published first.
- [ ] **P2.7f** A group whose last member leaves is orphaned — the document stays
      with an empty `members` array and nobody can reach it. Harmless today
      (Firestore charges nothing meaningful for it) but it should either be
      deleted or reaped.

### P2.6 — Nothing writes the group record `[B]` — ◐ *found 2026-08-24, written 2026-08-25*

**No code anywhere in `app/src/main` writes `recordKm`, `recordHolder` or
`recordDate`.** Every single occurrence is a read (`GroupRepository:217`), a
data-class field, a ViewModel mapping, or a Compose preview default. The only
Firestore writes that exist are group creation, membership, the `members`
arrayUnion, the `isPrimary` batch, and A's `dailyTotals` sync.

Consequences, all currently invisible because they render as empty states:

- S3.4 (Hall of Fame) renders its empty state permanently, not just until sync
  works.
- `LeaderboardViewModel.groupBestDay` is always null, so the group best-day line
  never appears.
- `isRecordImprovement()` — which we wrote, A reviewed, and we verified in the
  Rules Playground in both directions — guards a write that no client makes.

The sprint log called S3.4 "double-blocked" on A's sync and on the rules. Both
blockers have since been cleared and it still would not work, because a third
cause was never logged. It stayed hidden because the read path, the previews and
the security rules for the field all exist — the feature looks complete from
every angle except the one that matters.

- [x] **P2.6a** Decide where the record is written. *Done 2026-08-25 —
      `SyncViewModel.updateGroupRecords()`, after the totals sync so a day that
      has just become eligible is already in Firestore when its record lands.
      `GroupRepository.updateGroupRecordIfBetter()` does the write, strictly
      lower rather than `<=`: an equal value is not an improvement, and
      rewriting the doc would take the record from whoever set it first.*
- [x] **P2.6b** Settle whether it is A's or B's. *Resolved 2026-08-25: **B owns
      it end to end.** No edit-together seam is needed after all — `SyncViewModel`
      (`ui/`) already drives the sync cadence and `GroupRepository` (`firestore/`)
      already owns group-document writes, so both the trigger and the write sit in
      B's half. A's `ScrollRepositoryImpl` is not touched.*
- [x] **P2.6c** Only write a record for a *finished* day. *Done — and it turned
      out to be the smaller half of the problem. The record is a **minimum**, so
      **every** failure mode of the tracker produces a winning score, not just a
      partial day. Concretely: had this write existed on 2026-08-25, the crashed
      day would have set a group record of **5.5 m**, and `isRecordImprovement()`
      only permits `recordKm <= resource.data.recordKm` — so nobody could ever
      have beaten it and nobody could ever have raised it. A bad record is
      permanent short of editing Firestore by hand. `RecordEligibility` therefore
      fails closed on every rule: today, future days, zero-distance days,
      never-written timestamps, corrupt rows, and days whose last recorded scroll
      was before 18:00 local.*
- [ ] **P2.6d** Then P2.1d becomes testable — confirm on device that a record
      appears in Hall of Fame, and that the improvement-only rule behaves with two
      writers. Not yet run.
- [ ] **P2.6e** **The eligibility rule is a proxy, and worth improving.**
      "Tracking broke" and "I genuinely barely touched my phone" are the same
      shape in daily-totals data — both are just a small number — so no rule can
      separate them from what B can currently reach. The chosen gate is
      `DailyTotal.lastUpdated`, which is effectively "time of last scroll".
      A slightly better proxy, *distinct hour buckets in the day*, is not
      reachable from B's half: `ScrollRepository` exposes no per-day hour buckets
      (`getPeakHourForDay` returns only the top one). The real answer is per-day
      tracking health, which does not exist — `service_health` is a single
      current-state row with no history. Both would need `room/` work from A.
      Swapping `RecordEligibility.isPlausiblyComplete()` is the only change
      needed if either lands.

### P2.2 — Screens never run on a device `[B]`

Personal Records, Hall of Fame, App Breakdown, Weekly Recap and Settings are
compile-verified and reasoned about only. Home, Insights and the group flows have
had a real device pass.

- [ ] **P2.2a** Device pass on each of the five, with real data present.
- [ ] **P2.2b** And each with *no* data present — the empty state is where the
      honesty rule usually breaks.

### P2.3 — Errors reach the user `[B]` — ◐ *S2.8 closed 2026-08-24; the Snackbar work remains*

**Update 2026-08-24 — S2.8 is closed.** All four ViewModels that carry an
`errorMessage` now reach a screen: Create Group, Join Group, Leaderboard (with
retry), Hall of Fame (with retry) and Profile. The last two were the real gap —
they read Firestore through `getOrNull()`, so a failed read was
indistinguishable from an empty one and rendered as "no record set yet" and
"You're not in any groups yet" respectively. Telling someone in two groups they
are in none is a false statement, not a missing one.

**Still open:** there is no `Snackbar` anywhere in the app — still zero
occurrences. Every error state today is inline and permanent; there is no way to
surface a transient failure such as `setPrimaryGroup` failing, which still sets
an error message with no path to a human being.

- [x] **P2.3a** A `SnackbarHost` in `MainShell` and a shared way for any
      ViewModel to push a transient message to it. *Done 2026-08-25.
      `ui/ScrollaMessages.kt` is a singleton `SharedFlow` bus, matching how
      `ScrollaGraph` already works here rather than threading a callback through
      every ViewModel. The host sits **above** `AnimatedContent`, not inside
      `MainTabsScreen`'s `Scaffold` — inside it, a message raised on Settings or
      Hall of Fame or the group flows would never appear.*
      *It also fixed a live bug. `setPrimaryGroup`'s failure wrote to
      `LeaderboardUiState.errorMessage`, the same field a failed **load** uses,
      and `LeaderboardScreen` renders that field **instead of** the rows. One
      failed tap blanked a leaderboard that was on screen and perfectly valid,
      reporting a button's failure as the data's. It now raises a snackbar with
      a working Retry.*
- [x] **P2.3b** Route every existing `errorMessage` into a screen. *Done
      2026-08-24 — all four ViewModels now reach one.*
- [x] **P2.3c** Errors that are recoverable get a retry action, not just a
      complaint. *Leaderboard and Hall of Fame both retry.*
- [ ] **P2.3d** Distinguish "offline" from "failed" — nothing in the app checks
      connectivity at all (no `ConnectivityManager` usage anywhere), so a
      Firestore failure while offline still reads as a generic error. The
      snackbar makes this cheap to add now: the copy is the only missing part.

### P2.4 — `isServiceRunning` is inferred, not observed `[A]` — ☑ **done 2026-08-25**

Originally filed as an accuracy nitpick. It was not: on 2026-08-25 the
accessibility service **crashed** on a Xiaomi device and the health card read
"Tracking is active" for 9.5 hours, because `isServiceRunning` was a latch that
only ever got set true, `lastEventTimestamp` had never been written in the app's
life, and `isScrollAccessibilityServiceEnabled()` never consulted the master
switch. Three independent signals, all reporting healthy, none of them looking.

- [x] **P2.4a** Set the flag when the service actually connects. *Done by A —
      `onServiceConnected()` writes it directly, `onUnbind()`/`onDestroy()` clear
      it. `ServiceHealthDao` also moved to targeted `@Query` updates, which removed
      a lost-update race the original brief had not spotted: `flushBatch()` was
      writing the whole row back from a snapshot taken before its inserts, so a
      concurrent shutdown write would have been resurrected.*
- [x] **P2.4b** Revisit the UNKNOWN guard in `SettingsScreen`. *Removed 2026-08-25.
      It existed only because the flag was unreliable, and once the flag became
      trustworthy the guard could only hide real INTERRUPTED states. UNKNOWN is
      still reachable for a genuinely absent health row.*
- [x] **P2.4c** `lastEventTimestamp` is now written — a `@Volatile` in-memory stamp
      persisted at flush, so it and `lastRoomFlushTimestamp` come from different
      sources and diverge when events arrive but writes fail.
- [x] **P2.4d** On-device verification. *Confirmed working by B on the Xiaomi
      (serial 79CACEKN6TJJR84D) on 2026-08-25 — the device the crash happened on.
      Raw `service_health` rows were not captured, so `DEVICE_TEST_LOG.md` records
      this as B's confirmation rather than as a pasted matrix.*
- [ ] **P2.4e** Split `degradedReason` into flush and sync columns. Both
      subsystems share it, so a scroll flush clears a sync error within ten
      seconds and vice versa — and the new `onAccessibilityEvent` catch now writes
      there too, so an event error flickers rather than persisting. Pre-existing,
      but there are three writers now. A has it queued alongside P0.3.

### P2.5 — Push what exists `[B]`

- [x] **P2.5a** `b/group-flow-fixes` — pushed and merged as PR #11 (2026-08-25),
      14 commits. A's `a/service-health-detection` merged after it as `b59fef3`.

---

## P3 — Making it beautiful

### P3.1 — Strings live in Kotlin, not resources `[B]`

**151 hardcoded `Text()` strings; zero `stringResource` calls.** `strings.xml`
contains exactly one entry: `app_name`.

`android:supportsRtl="true"` in the manifest is therefore currently a claim the
app cannot honour — no string can flip, because no string is a resource.

`ScrollaStrings.kt` was the right instinct (centralised, reviewable copy) in the
wrong place. This is a mechanical move, not a rewrite.

- [ ] **P3.1a** Migrate `ScrollaStrings` into `res/values/strings.xml`.
- [ ] **P3.1b** Replace the `Text(ScrollaStrings.X)` call sites with
      `stringResource(R.string.x)`.
- [ ] **P3.1c** Plurals via `<plurals>` rather than string concatenation.
- [ ] **P3.1d** Numbers and dates through locale-aware formatting.
      `DistanceFormatter` hardcodes `Locale.US` — deliberate for the data
      contract, wrong for display. Separate the two.
- [ ] **P3.1e** Then either honour `supportsRtl` (test with RTL layout direction
      forced) or set it to `false` and stop claiming it.

### P3.2 — The app does not feel like anything `[B]`

**Zero `performHapticFeedback` calls in the entire app.** This is a large part of
what "premium" physically means on Android and it is roughly one line per site.

- [x] **P3.2a** Haptics on primary buttons and tab switches. *Done 2026-08-26.
      Wired into `ScrollaPrimaryButton` and `Modifier.bounceClick` — the two
      shared touch surfaces — rather than at forty call sites, so the whole app
      got it in three edits. Two weights only: a light tick for taps, a heavier
      one for confirmations, because a strong buzz on every tap reads as a broken
      phone rather than a premium one.*
      *Two corrections after the first device test, when nothing could be felt.
      **The device had touch feedback off** (`settings get system
      haptic_feedback_enabled` returned `0`), and `performHapticFeedback()`
      silently does nothing in that case — correctly, since an app that buzzes
      after someone has turned haptics off is rude, not premium. **Check that
      setting before suspecting the code.** Separately, the first version used
      Compose's `HapticFeedbackType.TextHandleMove`, which maps to
      `TEXT_HANDLE_MOVE` — meant for dragging a text selection handle, and
      treated as a no-op or rendered imperceptibly by several OEMs. Now uses
      platform constants directly through `LocalView`: `CLOCK_TICK` for taps and
      `CONFIRM` (API 30+, falling back to `LONG_PRESS`) for confirmations.*
- [ ] **P3.2e** Haptics on a record being broken. *Split out of P3.2a because it
      is genuinely harder: nothing currently knows a record was **just** broken.
      `updateGroupRecordIfBetter()` returns true, but it runs inside a background
      sync, so the moment it fires is not a moment the user is looking at
      anything. Needs a "new since you last looked" signal first. This is the
      app's emotional payload and it still lands silently.*
- [x] **P3.2b** Pull-to-refresh on the Leaderboard. *Done 2026-08-26.
      `refreshFromPull()` bypasses `LEADERBOARD_CACHE_STALE_MS` deliberately: the
      cache exists to stop **incidental** reads costing quota, not to overrule
      someone who has explicitly asked. `isRefreshing` is set only by the gesture,
      so the spinner belongs to the pull and not to the tab-open read.*
- [ ] **P3.2c** Audit the empty states. Only a handful of `isEmpty()` branches
      exist across all screens. Every list needs one, and it should say what to do
      next, not just that there is nothing.
- [ ] **P3.2d** Loading states: prefer skeletons over spinners on screens that
      have a known shape.

### P3.3 — Accessibility, actual `[B]`

65 `contentDescription`/`semantics` usages, 13 of them explicitly `null`. Some of
those nulls are correct (decorative icons); nobody has checked which.

- [ ] **P3.3a** A TalkBack pass over every screen. The app is *built on* the
      accessibility framework; being unusable with a screen reader would be a
      particular kind of embarrassing.
- [ ] **P3.3b** Verify each `contentDescription = null` is genuinely decorative.
- [ ] **P3.3c** Touch targets ≥48dp.
- [ ] **P3.3d** Test at 200% font scale. Fixed-height rows and the hero figure are
      where this will break.
- [ ] **P3.3e** Contrast check both themes. The light palette failed contrast once
      already and the fix has not been formally re-verified.

### P3.4 — Layout beyond one phone `[B]`

- [ ] **P3.4a** Landscape. Never tested.
- [ ] **P3.4b** Small screens and large ones. `minSdk 24` covers a lot of hardware.
- [ ] **P3.4c** Configuration-change survival — rotate on every screen and confirm
      nothing resets or re-fetches unnecessarily.

### P3.5 — The widget does not exist `[A]` — *this is S2.6*

No `AppWidgetProvider`, no Glance, nothing anywhere in `app/src`. Yet
`LeaderboardViewModel.setPrimaryGroup` is documented as *"Moves the primary flag,
which is what the widget reads."*

We have shipped the producer for a consumer that was never built.

- [ ] **P3.5a** Build it, or cut it and remove the primary-group machinery that
      exists only to serve it. Either is fine; the current state is not.

---

## P4 — Things the app says that are not true

### P4.1 — The onboarding hook is 3–7× reality `[B]`

`ScrollaStrings.kt:20` — `ONBOARDING_REVEAL_NUMBER = "2.8"`. Working from A's own
S0.7 rate, 2.8 km needs about seven hours of continuous scrolling. A heavy user
doing 2–3 real hours lands around 0.8–1.2 km.

This matters more than a normal copy bug because it is the first number a new
user ever sees, in an app whose entire discipline is "never a plausible fake
number", and because their own first real day will contradict it.

- [ ] **P4.1a** Settle the figure. The landmark table is already scaled correctly
      for reality: Empire State Building 443 m for an average day, Burj Khalifa
      830 m for a heavy one.
- [ ] **P4.1b** Consider the better hook we already own: *"the Burj Khalifa. With
      your thumb."* It is true, and vertical is more startling than a walk.
- [ ] **P4.1c** The fake leaderboard at `OnboardingScreen.kt:1014-1025` needs the
      same treatment — it shows 1.2 / 3.4 / 5.8 km, so the *winning* row is
      already at the top of a real heavy day and the losing rows are fiction.
- [ ] **P4.1d** `OnboardingScreen.kt:809` hardcodes "About 2,800 times further
      than you guessed" — derived from the same wrong number.

### P4.4 — Sub-metre distances rendered as "0 m" `[model/ — needs A sign-off]` — ☑ *fixed 2026-08-26*

Found in a real data export, not by reading code: Telegram at 0.4 m and the
system launcher at 0.3 m both displayed **"0 m"** on App Breakdown, which is
indistinguishable from an app that had never been scrolled at all.
`formatDisplayValue` used `%.0f`, so everything under half a metre became a
confident zero — in the app whose entire discipline is never showing a
plausible fake number.

- [x] **P4.4a** Render sub-metre values as `<1` rather than `0`.
- [x] **P4.4b** Keep the spoken form grammatical — TalkBack would otherwise read
      the literal "<1 metres". It now says "less than one metre".
- [◐] **P4.4c** A's sign-off. *Self-reviewed by B on 2026-08-26 (REVIEW_LOG #7)
      because A was unavailable, and logged as such rather than attributed to A.
      **Still wants a second pair of eyes** — §2 exists because reviews #2 and #4
      each caught a real bug, #4 being that `allow create` does not grant `allow
      update`, which meant group joining had never worked at all.*

### P4.2 — Fake numbers still in the source `[B]`

- [x] **P4.2a** Fake preview defaults removed. *Done 2026-08-26 and it was worse
      than the two sites originally logged. `HomeScreen` also defaulted
      `rankPosition = 2`, `HallOfFameScreen` defaulted to a record held by
      "Lewis" on "July 12" with `hasRecord = true`, and `ProfileScreen` carried a
      full fake profile — name, best day, seven-day average, group count, group
      name. All now null or zero. A default that renders plausibly is one
      forgotten argument away from being shown to a user as their own data, and
      it would look like working software.*

### P4.3 — Dead weight `[Both]`

- [x] **P4.3a** `libs.firebase.ai` removed. *Zero usages; it was shipping in
      every APK.*
- [◐] **P4.3b** The written-but-never-rendered string audit. *Re-run 2026-08-26:
      **43 unused of 214**, down from 58. It has now paid off three times — it
      found the delete-flow copy (including the type-to-confirm hint we adopted),
      and the "[deleted]" record promise. Two more it surfaces now:
      `ERROR_NO_CONNECTION` is unused, which is exactly P2.3d; and the join
      errors exist as two parallel sets (`GROUP_JOIN_ERROR_*` and
      `JOIN_GROUP_ERROR_*`), one used and one not. Several others name features
      that do not exist — `LEADERBOARD_MOST_CONSISTENT`,
      `SETTINGS_WIDGET_GROUP_*`, `HOME_INSIGHT_PERSONAL_BEST_LABEL` — so the
      rotating insight card advertised in S2.1 has one of its four types built.*

---

## Suggested order

Not the order above — that is by severity. This is by what unblocks what.

1. **P2.5a** push the branch (five minutes, and everything else is downstream)
2. **P2.1** the second-account test — nothing social is real until this passes
3. **P0.1b/c** the two test files that cover our actual bug history
4. **P0.3** Crashlytics, before any more APKs go to friends
5. **P0.4** account deletion
6. **P4.1** settle the onboarding number before anyone else installs
7. **P2.3** error surfacing (S2.8)
8. **P0.5a** the Play decision — do it before P0.2 and P3.1, since it determines
   how much either is worth
9. **P0.2** release build hardening
10. **P3.x** polish, in whatever order is most satisfying

---

## Not audited

So this list is not mistaken for exhaustive. None of the following has been
looked at yet:

- Battery impact of the accessibility service over a full day
- Memory profiling; the two heap dumps in the repo root suggest someone started
- Cold-start time (`installSplashScreen` is wired; the number is unmeasured)
- Room query performance as the event table grows — nobody has run this for a
  month of data
- Firestore read/write cost against the free tier at more than one user
- What happens on a device where the OEM kills the service aggressively
  (`DEVICE_TEST_LOG.md` wants three manufacturers; it has one)
