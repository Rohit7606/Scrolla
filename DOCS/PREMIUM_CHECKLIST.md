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

### P0.1 — Tests exist at all `[Both]` — ◐ **79 tests as of 2026-09-06**

*(Counted from the actual JUnit XML rather than by hand: `ScrollDeltaTest` 15, `DistanceFormatterTest` 14, `RecordEligibilityTest` 12, `DataExportTest` 10, `GroupStandingTest` 10, `WeeklyWindowTest` 10, `HomeInsightsTest` 8 — 79 total, 0 failing. The "54" this line carried on 2026-09-04 was itself a hand-count that missed `WeeklyWindowTest`; the real figure was 64 before the audit fixes added `ScrollDeltaTest`. Hand-counting `@Test` annotations has now been wrong twice, so this number comes from `app/build/test-results/` from here on. Still **zero** `androidTest` — no instrumented test has ever run.)*

*`ScrollDeltaTest` is the one that matters most: it covers the sensor's core arithmetic, which had **no test at all** until 2026-09-06 despite being the thing the entire product measures.*

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

### P0.4 — Delete account is a dead row `[B]` — ☑ **built 2026-08-25, rules confirmed live 2026-09-06**

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
- [x] **P0.4b** Firestore rules for self-deletion. Reviewed and approved by A 2026-08-25 (REVIEW_LOG #5). **Deployment confirmed 2026-09-06:** the live rules were pasted out of the Firebase console and diffed against the committed file — byte-identical, including `isSelfLeave`, `isGroupRename` and the split `create, update` / `delete` on `dailyTotals`. **This checklist claimed "not deployed" for eleven days and was wrong**; delete account, leave group and rename group have not been blocked at the server for some time. Nobody had checked, on either side — the doc was treated as the source of truth about a system it does not control. Verify against the console, not against this file.
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

### P0.5 — Play policy — ☑ **DECIDED 2026-08-26: sideload permanently**

Two separate problems, one already flagged in the manifest and one not.

`QUERY_ALL_PACKAGES` is Play-restricted; the manifest comment says so and says
sideloading makes it fine today. Correct.

The unflagged one is bigger: **Google restricts `AccessibilityService` to genuine
accessibility purposes**, and measuring scroll distance is not one. This is not a
detail to discover during review.

- [x] **P0.5a** **Decision: Scrolla stays a sideloaded APK handed to friends. It
      is not going on the Play Store.**

      *The reasoning, so it does not have to be re-argued.* Play restricts
      `AccessibilityService` to genuine accessibility purposes, and measuring
      scroll distance is not one. This cannot be engineered around:
      `UsageStatsManager` gives screen time and app launches but **not scroll
      distance**, so the app's entire central metric exists only through the
      restricted API. Listing would be a real gamble on the app's core, not a
      formality.

      In Scrolla's favour, for the record: the service config is the most minimal
      version possible — `accessibilityEventTypes="typeViewScrolled"` and
      `canRetrieveWindowContent="false"`, so it cannot read screen content at
      all. If this decision is ever revisited, that is the justification to lead
      with.

      **The decision is reversible.** Choosing sideload now does not prevent a
      listing later; it stops us paying Play's costs today for a listing that may
      never happen.
- [—] **P0.5b** ~~Replace `QUERY_ALL_PACKAGES` with `<queries>`~~ — **not needed
      under P0.5a.** Keeping it means App Breakdown shows real app names for
      every app rather than a curated subset. Revisit only if the Play decision
      changes.
- [—] **P0.5c** ~~Hosted privacy policy~~ — **not required under P0.5a.** The
      in-app version below still is.
- [ ] **P0.5d** **An in-app privacy screen. Still required, and now the only
      P0.5 item left.** Sideloading removes Google's requirement, not the
      obligation — the people installing this are handing an accessibility
      service to a friend's APK on trust, which is a higher bar than a store
      listing, not a lower one. It should be honest about what the service can
      technically see (`typeViewScrolled` only, no window content) versus what
      Scrolla stores and uploads. The two `..._PRIVACY` strings already shown are
      good and are not sufficient.

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

### P2.16 — Signing in did not protect anything `[B]` — ◐ *built 2026-09-06, rules not yet deployed*

Sign-in bought the user groups and a leaderboard. It did not protect their data,
and nothing said so.

Their totals did reach Firestore, but only as a side effect of group membership:
`triggerFirestoreSync()` writes inside `for (groupDoc in userGroups)`, so a user
in **no group synced nothing at all**, and the documents lived under the group
rather than the person. **Nothing ever read them back** — there was no restore
path anywhere in the app. An uninstall, including an accidental one, took
everything.

- [x] **P2.16a** `users/{uid}/dailyTotals/{day}` — the personal copy, one small
      document per day, written regardless of group membership. Strictly private:
      unlike the group copy there is no `allow read` for other signed-in users.
- [x] **P2.16b** Two-way reconcile on launch (`BackupReconcile`, 8 tests).
      **The device wins every disagreement.** That is not a coin toss: this is a
      lowest-wins leaderboard, so restoring a stale smaller value over a real day
      hands the user a record they did not earn, and `isRecordImprovement()` only
      permits equal-or-lower — no client could ever raise it back. Float
      comparison uses a tolerance, or the Float→double→Float round trip would
      re-upload the whole history on every launch.
- [x] **P2.16c** Account deletion covers the new store. A backup outliving its
      account would be unreachable *and* undeletable, since every rule here is
      gated on `request.auth.uid` — an orphan created by the flow whose purpose
      is removal.
- [ ] **P2.16d** **Deploy the new rule block.** Until then every write to this
      collection is denied and the feature is inert — rule 1's `allow write` on
      `/users/{userId}` does **not** cascade to subcollections in Firestore.
      Needs A's review first (AGENTS.md §2), logged as REVIEW_LOG #10.
- [ ] **P2.16e** Untested against a real reinstall. The write and reconcile paths
      are unit-tested and build-verified; nobody has yet wiped a device and
      watched the history come back.

**Not backed up, deliberately:** `scroll_events` — which apps, at which hour.
This adds no new category of data to the server, only a personal copy of what the
leaderboard already published. Android Auto Backup covers per-app history
separately, into the user's own Drive quota; verified capturing 445 KB
successfully on 2026-09-06, restore half not yet tested.

### P2.8 — `app_totals` is a dead table `[A — `room/`]` — ☑ *found 2026-09-04, deleted 2026-09-06*

> **Resolved:** the entity and its table are gone (schema v4, `MIGRATION_3_4`).
> Deleting won over populating because `scroll_events` is now kept indefinitely
> (see DATA_CONTRACT §Retention), which makes a precomputed per-app summary a
> cache for a table that is already there. Verified on the device: `app_totals`
> absent, `user_version` 4, all 2,069 events and 8 daily totals intact.


`AppTotal` is declared in `ScrollaDatabase`, carried through the migrations, and
specified in `DATA_CONTRACT.md` §2.1 as "one row per (day, app), **recomputed
alongside DailyTotal**". A device pull after 1,721 real events found **zero rows**,
because nothing in `app/src/main` reads or writes it — every occurrence is the
entity file and the database registration.

App Breakdown is unaffected: `getTodayTopApps()` aggregates from `scroll_events`
via `getTopAppsByDay()`. So this is dead weight plus a contract that documents
behaviour that does not happen — not a broken screen.

This is the **fifth** instance of the pattern (nothing wrote `recordKm`; the join
code was discarded by `MainShell`; `hasData` was computed and never accepted;
fake preview defaults survived their own sweep). Each was found by grepping for
the writer rather than reading the prose.

- [ ] **P2.8a** Either populate it in `flushBatch()` alongside the `DailyTotal`
      recompute, or delete the entity and correct `DATA_CONTRACT.md` §2.1. Deleting
      is the smaller change and nothing needs the table today; populating is right
      only if a per-app historical view is actually wanted. **A's call** — it is
      `room/`, and it needs a migration either way.

### P2.9 — Weekly Recap called thirteen days "this week" `[B]` — ☑ *found and fixed 2026-09-04*

Seen on the device, not in the code: the recap's hero figure read **595 m "this
week"** when the true seven-day total was **1 m**. The arithmetic was right and
the window was wrong.

`getRecentDailyTotals(7)` is `ORDER BY day DESC LIMIT 7` — the seven most recent
**rows**. A day with no scrolling has no row, so seven rows can span any amount
of calendar time. Here they spanned 23 Aug → 4 Sep and the recap summed the lot.

**The project had already solved this once.** S3.3 records that Personal
Records' best-week card walks *consecutive calendar dates rather than consecutive
rows*, because "a day with no scrolling has no row, so a row-based window would
silently span a gap and report a week that never happened." The identical trap
sat unfixed one screen over.

- [x] **P2.9a** `WeeklyWindow` — pure, clock-injected, 10 tests, including the
      exact rows pulled off the device.
- [x] **P2.9b** `hasData` follows the filtered window, so an empty week reaches
      the empty state rather than borrowing a figure from a fortnight ago.
- [ ] **P2.9c** **Audit the other `getRecentDailyTotals` callers.** Insights is
      safe — `buildWeek` looks rows up *by date* into a map, so gaps become zeros
      rather than shifting the window. But the interface comment still promises
      "daily totals for the last N days" while the query returns N rows, and that
      mismatch is what produced this bug. Either rename it or make it mean what it
      says. `model/`-adjacent and A owns the DAO, so it needs agreeing.

### P2.10 — Scrolla counts its own scrolling `[A — `service/`]` — ☐ *found 2026-09-04*

`scroll_events` holds **19 rows for `com.scrolla` itself** (54.6 cm all-time),
including 10.6 cm generated by scrolling the Settings screen during the device
pass that found it. `com.android.systemui` is in there too — the notification
shade.

On a reverse leaderboard where the lowest number wins, **opening the app to check
your standing makes your standing worse.** The magnitude is trivial; the
self-reference is not, and it is the kind of detail someone notices and loses
trust over.

- [ ] **P2.10a** Exclude the app's own package in the service. Whether to also
      exclude `com.android.systemui`, launchers and the keyboard is a product
      question — a case exists for counting the shade as real scrolling — but
      counting Scrolla itself has no case at all.

### P2.11 — The group record is about to be set by an untracked day `[B]` — ☐ *urgent, found 2026-09-04*

**P2.6d passed**: Hall of Fame renders a real record — "You · 104 m · 25 Aug" —
so the write, the eligibility gate and the read path all work end to end on a
device. That is the good news and it closes a checklist item.

The bad news is *which* day holds it, and which day is next.

- The current record, **25 Aug**, is the day the service **crashed at 09:34** and
  stayed dead for 9.5 hours. It passed `isPlausiblyComplete` legitimately, via the
  ran-past-midnight branch, because scrolling resumed that evening after B
  toggled the service back on.
- **Today, 4 Sep, becomes eligible tomorrow.** The service was dead from 27 Aug
  until it was reinstalled at 17:47 today, so the day captured about twenty-five
  minutes and totals **3.2 m**, with `lastUpdated` at 18:11 — past the 18:00
  cutoff. Every rule passes.
- `isRecordImprovement()` permits only `recordKm <= resource.data.recordKm`, so
  once **3.2 m** lands, **nobody in the group can ever beat it** and nobody can
  raise it. A bad record is permanent short of editing Firestore by hand.

This is not a new defect — it is exactly the limitation P2.6e already describes,
arriving on a specific date with real numbers. It is filed separately because
P2.6e is a design note and this is a thing that happens tomorrow.

- [ ] **P2.11a** Decide before the next sync: hand-correct the record in
      Firestore, accept it while the group is still just a test group, or hold the
      record write until P2.6e's better signal exists.
- [ ] **P2.11b** The real fix stays P2.6e — per-day tracking health, which needs
      `room/` work from A. Every proxy reachable from B's half treats "the tracker
      died" and "a genuinely quiet day" as the same shape, because in
      `daily_totals` they are.

### P2.12 — Tracking stopped silently, for days `[Both]` — ◐ *detection built 2026-09-04, cause fixed 2026-09-05*

**The complaint, verbatim: "the scroll tracking stops working even when the
toggle is on."** A screen recording on 2026-09-05 shows exactly that — Android's
own Accessibility → Downloaded apps list reads **"Scrolla — Not working. Tap for
info."**, and inside, above an **enabled** toggle, **"This service is
malfunctioning."**

`dumpsys` agrees: `Enabled services` contains us, `Binding services` is empty,
`Crashed services` contains us, and `accessibility_enabled` is `0`. The framework
switches the master accessibility switch off when its only enabled service dies,
but the per-service toggle keeps rendering as on. So the UI a user checks is the
one surface that cannot tell them.

Measured gaps on B's Xiaomi: **9.5 hours** (2026-08-25) and **18 hours**
(events stopped 2026-09-04 18:46:50, noticed the next midday).

**Two separate problems, fixed separately.**

**(a) Nothing told anyone.** Home did not observe health at all, so the app
looked entirely normal with tracking dead.
- [x] **P2.12a** In-app banner across every tab whenever the accessibility switch
      is off, deep-linking to accessibility settings. Driven by the health row
      `MainActivity.onResume` already refreshes, so returning from Settings clears
      it. Shown for an explicit `false` only — a null row is "not checked yet" and
      must not flash a false alarm on cold start. `[B, ui/]`
- [x] **P2.12b** `TrackingHealthWatcher` — an AlarmManager check every ~15 min
      that posts one high-priority notification when the service is down while the
      app is closed, and clears it when healthy. Inexact, non-wakeup, no
      exact-alarm permission; re-armed on boot and launch. `[A's device/ — needs
      retro-review]`
- [x] **P2.12c** Request `POST_NOTIFICATIONS` at launch. Fixes DEVICE_TEST_LOG
      bug #1 in passing: the foreground notification never showed on a fresh
      install because nothing asked.
- [x] **P2.12d** **Confirmed working in the field.** The 2026-09-05 recording
      shows the notification — *"Scrolla stopped tracking · Tap to turn it back on"*
      — fired at 12:19. The detection half is proven on a real outage.

**(b) Why it died.** Three unguarded paths, any of which kills the process, and
killing the process is what kills the binding.
- [x] **P2.12e** `serviceScope` was `Dispatchers.IO + Job()` with **no
      `CoroutineExceptionHandler`**. An uncaught exception in a `launch` reaches
      the thread's default handler, which kills the process. Plain `Job()` made it
      worse — one child failing cancels every sibling, so even surviving would
      leave every later flush a silent no-op. Now `SupervisorJob` + a handler that
      logs and marks degraded.
- [x] **P2.12f** `flushBatch` opened the database **outside its `try`** — alone
      among the five launch blocks in the file — on the hot path that runs every
      50 events or 10 seconds. Moved inside; the catch re-acquires rather than
      reuses, since opening is now what may have failed.
- [x] **P2.12g** `startForeground` ran unguarded on `onServiceConnected`, the
      service's entry point. Note `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` is an
      **API 34** constant and the test device is **API 33**.
- [ ] **P2.12h** **Not yet proven against a captured stack trace.** The crash
      buffer was 256 KB and had rotated past both outages. Raised to **16 MB** on
      the test device — the same lesson as the S0.7 Logcat truncation, applied to
      crashes. Confirm the fix by running a full day without an outage, and
      capture the trace if one still happens.
- [ ] **P2.12i** This is the argument for **P0.3 (Crashlytics)** in one line: two
      outages, both unrecoverable, on the one device we physically hold. On a
      friend's phone there would have been nothing at all.

### P2.15 — MIUI kills the foreground service, and nothing restarts it `[Both]` — 🔴 *root cause, confirmed 2026-09-06*

**Supersedes P2.12's root-cause half.** P2.12e–g fixed three real latent bugs but
**they were not the cause** of the outages, and must not be recorded as the fix.

With the crash buffer raised to 16 MB, a 22-hour window was recoverable and shows
**no Scrolla crash at all** — the only `FATAL EXCEPTION` in it belongs to WhatsApp.
What it shows instead:

```
18:01:37  ProcessSceneCleaner: OneKeyClean: kill procName=com.scrolla info=AS:504
18:01:37  ActivityManager: Killing 24098:com.scrolla/u0a315 (adj 50): OneKeyClean
18:01:37  ActivityManager: Cancel FGS notification … ChannelId:scrolla_tracking
```

`adj 50` is a **foreground-service** process, and the FGS notification was torn
down as a consequence — so MIUI killed a live, healthy foreground service by name.
Observed killers in 22 hours: `LockScreenClean` ×6 (**the app dies when the screen
locks**), `camera boost` ×5, `OneKeyClean` ×2, `lowmemorykiller` ×3.

**Battery whitelisting is irrelevant here.** Scrolla is whitelisted and in standby
bucket 5 (EXEMPTED); those govern AOSP Doze/App Standby, and MIUI's cleaners sit
outside that framework entirely.

Android rebound the service after one kill but not the next — and once it stops
rebinding, the master switch drops to 0 and you get "This service is malfunctioning"
above an enabled toggle. `DEVICE_TEST_LOG` §3.2 names the likely reason: **MIUI's
Autostart is off by default for third-party apps, and without it nothing can restart
the service after a kill.**

- [ ] **P2.15a** Make the app **ask for the four MIUI settings**, not just describe
      them generically: Autostart on, **lock the app in Recents** (the highest-value
      one — it is what makes OneKeyClean and LockScreenClean skip a process),
      battery saver unrestricted, Security-app background restriction off. S1.A7's
      screen currently shows generic instructions on this device; §7 of the device
      log is still unpopulated for Xiaomi.
- [ ] **P2.15b** Detect the OEM and gate the copy on it. `Build.MANUFACTURER` is
      already read for the health card, so the plumbing exists.
- [ ] **P2.15c** **Re-frame what the app can promise.** On MIUI, tracking is
      best-effort no matter what the code does. The honest options are to say so, or
      to treat gaps as expected and stop presenting a day's total as complete when
      the service was dead for part of it — which also feeds P2.11 and P2.6e, since
      a killed day produces a low number that looks like a winning score.
- [ ] **P2.15d** This is a **distribution** problem: four undiscoverable settings,
      per device, per friend, none of which the app currently asks for. It belongs
      in the sideload gate in `DEVICE_TEST_LOG` §1, not only in a checklist.

### P2.13 — UPI apps refuse to run while Scrolla is enabled `[Both]` — ☐ *no code fix exists*

**supermoney blocks payments and names Scrolla explicitly:** *"Turn off
accessibility access — These apps can control your screen or tap on buttons.
Turn it off to protect your UPI payments. **Scrolla** → Open settings."*
Google Pay India and HDFC are installed on the same device and enforce the same
NPCI/RBI-driven anti-fraud check.

**This is structural and cannot be engineered around.** Scroll distance exists
only through `AccessibilityService` — the same P0.5a reasoning that took Scrolla
off the Play Store — and that is precisely the API payment apps block on.
Allowlisting is granted to genuine accessibility tools, which Scrolla is not.
**Evading the detection is not an option**: it is an anti-fraud control, and
defeating it is what the malware it exists to stop does.

It also feeds P2.12: the user turns Scrolla off to pay and does not turn it back
on, so tracking stops for reasons that look identical to a crash.

- [ ] **P2.13a** Decide the product answer. Three honest options: accept it and
      make re-enabling fast (P2.12's banner already does much of this); add a
      deliberate "pause for payments" affordance so the disable is intentional and
      reminded; or reconsider whether a UPI-using friend group is the right test
      cohort. That last one is a real question — in India this is not an edge case.
- [ ] **P2.13b** Say it in onboarding. Someone handed an APK deserves to know
      their payment app may object *before* they grant the permission, not after.

### P2.14 — A-track audit findings `[A]` — ◐ *audited 2026-09-05, fixed 2026-09-06, full detail in `DOCS/AUDIT_A_TRACK.md`*

B audited everything A owns after the third silent outage, on the principle that
the outage's cause — an unguarded line in the hottest path — was unlikely to be
the only one of its kind. Logged as the mandatory **M1 review #8, verdict Changes
required**. Eleven findings; the first four matter.

**Status 2026-09-06:** **all eleven closed.** Nine were fixed as defects (review
#9); the remaining two were decisions rather than defects and were taken the same
day — `scroll_events` is **kept indefinitely** with stated copy and a user-facing
clear action, and `AppTotal` is **deleted**. See DATA_CONTRACT §Retention for the
reasoning and the numbers behind it. **All of these changes are in A's layer and
need A's retro-review**, per AGENTS.md §2.

- [x] **P2.14a 🔴 The RecyclerView reset guard does not guard.** Its `if` body
      contains only a `Log.d`; `computed` is returned unchanged and `pxToCm`
      applies `Math.abs()`, so a view recycle contributes its full jump as
      phantom distance. **`abs()` is only safe because of this guard.** It
      contradicts S0.5 and the M1 checklist item ticked in review #1 — both were
      satisfied by watching the log line appear, not by checking the distance was
      excluded. Device data is consistent: 29 % of Chrome batches and 9 % of
      Reddit's exceed 100 cm (max 512 cm in ten seconds ≈ 34 screen-heights)
      against 1.5 % for Instagram, which uses the `scrollDeltaY` path where no
      reset detection exists at all. **Every accuracy figure in
      `SENSOR_PROGRESS.md` was measured with this present** and is biased high for
      `scrollY`-path apps — S0.7 needs re-running after the fix.
      **Fixed 2026-09-06.** A reset now moves the baseline and contributes zero.
      The arithmetic moved into `service/ScrollDelta.kt` as a pure function with
      no Android types, and `ScrollDeltaTest` (15 tests) asserts on the
      **returned delta** rather than on a log line — which is the specific thing
      that let this pass review twice. S0.7/S0.8 re-run still outstanding.
- [x] **P2.14b 🟠 `getDatabase()`'s double-checked lock is missing its second
      check**, so two threads can each build a Room instance over the same file —
      two connection pools, one database. Called concurrently from the service
      (five sites), both receivers, `MainActivity` and the repository. A plausible
      source of the exception behind the outages; complements rather than replaces
      the `try` fix in `54ac926`.
      **Fixed 2026-09-06** — standard `INSTANCE ?: synchronized { INSTANCE ?: build().also { INSTANCE = it } }`.
- [x] **P2.14c 🟠 "Your lowest day ever" is usually today.** `MIN(totalKm)` and
      `ORDER BY totalKm ASC LIMIT 1` do not exclude the current, partial day.
      Proven on device 2026-09-05: returns **today at 4.06 m** where it should
      return 2026-09-04 at 21.55 m. So the app announces a new personal record
      every morning. `RecordEligibility` guards exactly this for the **group**
      record; the personal record has nothing — the same trap one screen over, as
      with P2.9.
      **Fixed 2026-09-06.** Both queries take `WHERE day < :today AND totalKm > 0`;
      `today` is supplied by the repository so the A/B contract signatures are
      unchanged. Verified against the device database the same day: the old query
      returned **2026-09-06 at 6.75 m** (today, four hours old), the new one
      returns **2026-09-04 at 21.55 m**. All four call sites already handled null
      and now say something true on day one instead of crowning the first
      morning a record.
- [◐] **P2.14d 🟡 Every `scroll_events` read is a full scan, and the table is
      unbounded.** No index on `day`; `EXPLAIN QUERY PLAN` returns `SCAN
      scroll_events` for the query Home runs on every load. Measured growth ~400
      rows/day ≈ **146,000 rows/year**. `deleteOlderThan()` exists and **is never
      called from anywhere**, so nothing bounds the most sensitive store in the
      app — which is also a retention question the privacy copy does not answer.
      **Index fixed 2026-09-06** — `@Index("day")` plus `MIGRATION_2_3`, schema
      version 3. Verified by upgrading the real device database in place rather
      than by inspection: 2044 events and 8 daily totals survived, `user_version`
      went 2 → 3, and the plan for Home's query changed from `SCAN scroll_events`
      to `SEARCH scroll_events USING INDEX index_scroll_events_day (day=?)`.
      **Retention is still open and is a decision, not a defect** — see below.
- [x] **P2.14d-retention 🟡 Nothing bounds `scroll_events`.** **Decided
      2026-09-06: keep it indefinitely, and say so.** Measured first — 76 bytes a
      row, ~255 rows/day, **6.7–11 MB/year** against a ~32 MB APK — which rules
      out storage as a reason either way. Deletion is irreversible and a retention
      policy can be added at any time, and no per-app history feature has been
      designed yet, so bounding it now would trade an unrecoverable asset for a
      few megabytes. Keeping it carries two obligations and **both are shipped**:
      the privacy copy now states the duration instead of implying it, and
      Settings → *Clear app history* gives the user the undo (`clearAppHistory()`,
      which keeps today so Home does not read 0 m against a non-zero daily total).
      Full reasoning in `DATA_CONTRACT.md` §Retention. **Revisit before real
      users**, not before more features.
- [x] **P2.14e 🟡 `AppTotal` has no DAO at all** — no `appTotalDao()` accessor and
      no DAO type, so nothing could write it even in principle. Supersedes P2.8
      with the stronger finding. **Deleted 2026-09-06** (schema v4,
      `MIGRATION_3_4`). Deleting won over populating precisely *because* of the
      retention decision above: with the raw events kept forever, a per-app
      summary is a cache for a table that is already present, and the three
      features it would serve — per-app trends, peak-hour history, year-in-review
      — are a `GROUP BY` away from `scroll_events` with no new plumbing. Verified
      on device: table gone, data intact. `DATA_CONTRACT.md` §2.1 corrected.
- [x] **P2.14f 🟡 A failed UI read marks the *tracking service* degraded**, via
      `markDegraded` → `markSyncFailed`. Conflates "a query failed" with "tracking
      is broken" on the one card whose job is to be trustworthy about that, and
      mislabels reads as sync failures. Makes P2.4e three writers, not two.
      **Fixed 2026-09-06** — `markDegraded` and its nine call sites removed;
      reads no longer write service health at all. AGENTS.md §4.8 ("fail loud
      internally") still holds, because every one of those catch blocks already
      logged at error level immediately above the removed line. Surfacing read
      failures properly needs its own column, which is P2.4e. **`DATA_CONTRACT.md`
      §4 still documents the old behaviour and needs A's sign-off to update.**
- [x] **P2.14g 🟡 No foreground service below API 30 despite `minSdk 24`.**
      `startForeground` is gated on `Build.VERSION_CODES.R`, so on Android 7–10
      there is no persistent notification and no foreground priority — the app
      installs, appears to work, and tracks almost nothing. Support them or raise
      `minSdk`. **Fixed 2026-09-06 by supporting them**, not by raising `minSdk`:
      the 2-arg `startForeground` is called below API 30, inside the same
      try/catch. Raising `minSdk` was the alternative but that is a distribution
      decision, and given that MIUI killing a *foreground* service is already this
      project's main outage cause, shipping no foreground service at all was
      strictly worse. Untested on a real API 24–29 device — nobody has one.
- [x] **P2.14h ⚪** Dead code from the health refactor: `getOnce()`,
      `getTotalCmBetweenDays()`, and a retained whole-row `upsert()` that is the
      exact pattern A's decision log #4 was written to eliminate.
      **Fixed 2026-09-06** — all three deleted after confirming zero callers.
      `deleteOlderThan()` was deliberately kept: it is uncalled too, but it is the
      mechanism the retention decision above will need.
- [x] **P2.14i ⚪** `lastKnownScrollY` is never pruned, and
      `android:exported="false"` on the accessibility service deviates from the
      documented norm (works on three devices — flag, do not change blind).
      **Pruning fixed 2026-09-06** — now an LRU `LinkedHashMap` capped at 500
      views. Eviction is cheap by construction: a re-encountered key is treated
      as first-seen, sets a baseline and contributes zero for one event.
      **`exported="false"` deliberately unchanged**, as the audit recommended.

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

### P3.1 — Strings live in Kotlin, not resources `[B]` — ⬇ **deprioritised by P0.5a**

**151 hardcoded `Text()` strings; zero `stringResource` calls.** `strings.xml`
contains exactly one entry: `app_name`.

`android:supportsRtl="true"` in the manifest is therefore currently a claim the
app cannot honour — no string can flip, because no string is a resource.

`ScrollaStrings.kt` was the right instinct (centralised, reviewable copy) in the
wrong place. This is a mechanical move, not a rewrite.

**Re-scoped 2026-08-26.** With Scrolla sideloaded to one friend group rather than
listed (P0.5a), localisation stops being load-bearing and this drops from "the
biggest remaining job" to "worth doing if the app ever needs another language".
The migration is ~151 call sites for no user-visible change today. **One part
does survive the re-scope:** `android:supportsRtl="true"` in the manifest remains
a claim the app cannot honour, since no string is a resource and none can flip.
Either do P3.1e or set it to `false` — the app's own discipline is not to claim
things that are not true.

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
      *Three corrections after device testing, and the last one mattered most.
      **The device had touch feedback off** (`settings get system
      haptic_feedback_enabled` returned `0`), and `performHapticFeedback()`
      silently does nothing in that case — correctly, since an app that buzzes
      after someone has turned haptics off is rude, not premium. **Check that
      setting before suspecting the code.** Separately, the first version used
      Compose's `HapticFeedbackType.TextHandleMove`, which maps to
      `TEXT_HANDLE_MOVE` — meant for dragging a text selection handle, and
      treated as a no-op or rendered imperceptibly by several OEMs. Now uses
      platform constants directly through `LocalView`: `CLOCK_TICK` for taps and
      `CONFIRM` for confirmations. **Then the premise turned out to be wrong.**
      `haptic_feedback_enabled` gates *touch feedback* — keyboard taps, system UI
      — not app-initiated vibration, which is a separate channel. Apps that feel
      good on Android use the vibrator directly, which is why Kuvera's haptics
      work with that switch off. Scrolla now does the same: `VibrationEffect
      .createPredefined(EFFECT_TICK / EFFECT_CLICK)` through `VibratorManager`,
      with the `VIBRATE` permission (normal, no runtime prompt).*
      *Bypassing a system setting while offering no alternative would be worse
      than respecting it, so Settings now has a **Haptic feedback** toggle, on by
      default. The system's vibration-intensity setting and Do Not Disturb still
      apply, so the OS keeps the final say on strength.*
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
- [◐] **P3.2c** Audit the empty states. *Counted per screen 2026-08-26: Home 5,
      Leaderboard 4, Personal Records 4, Profile 3, Hall of Fame 3, App Breakdown
      3, Insights 2 — and **Weekly Recap 0**. Fixed: `WeeklyRecapViewModel` had
      always computed `hasData` and `WeeklyRecapScreen` did not accept the
      parameter, so a week with nothing recorded rendered a confident hero figure
      of "0 m" and offered it for sharing. It now shows a dash and different copy.
      Same shape as the join code that was generated and then discarded by
      `MainShell` — worth grepping for other state a ViewModel computes and no
      screen takes.*
- [ ] **P3.2g** The remaining screens' empty states still say only that there is
      nothing, not what to do next.
- [ ] **P3.2d** Loading states: prefer skeletons over spinners on screens that
      have a known shape.

### P3.2f — The rotating insight card did not rotate `[B]` — ☑ *fixed 2026-08-26*

S2.1 asked for "a rotating insight card wired to ≥ 1 real insight type". One type
was built, so the clause passed while the card never rotated — and three of the
four labels written for it (`HOME_INSIGHT_PERSONAL_BEST_LABEL`,
`HOME_INSIGHT_QUICK_WIN_LABEL`, `HOME_INSIGHT_PLACEHOLDER_*`) had never been
rendered. `MainShell` hardcoded the peak-hour type, so when `peakHour` was null
the card rendered nothing at all, despite placeholder copy existing for exactly
that case.

- [x] **P3.2f-a** `HomeInsights.select()` — a pure, clock-free function over
      three real insight types plus the placeholder. 8 tests.
- [x] **P3.2f-b** Never invent: a personal-best insight only when today is
      genuinely under the best day, a quick win only when today is below
      yesterday, and no comparison at all when there is no row for yesterday.
- [x] **P3.2f-c** Rotate deterministically on day-of-year, so the card is stable
      for a whole day. One that changes on every recomposition reads as a bug.

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

> **Measured data now exists, and it is worse than 3–7×.** Pulled from the
> Xiaomi on 2026-09-04 — five consecutive days of one real user's `daily_totals`,
> the first multi-day record the project has had:
>
> | Day | Distance |
> |---|---|
> | 2026-08-23 | 152 m |
> | 2026-08-24 | 106 m |
> | 2026-08-25 | 104 m *(crash day — tracking died at 09:34, so a floor)* |
> | 2026-08-26 | **205 m** *(heaviest)* |
> | 2026-08-27 | 26 m *(partial)* |
>
> The heaviest full day is **205 m**. `ONBOARDING_REVEAL_NUMBER = "2.8"` km is
> **~13.7× that** — and it is the first number a new user sees, in an app whose
> discipline is never showing a plausible fake one. Two honest caveats: the
> service was crashed or disabled for stretches of this window, so every figure
> is a lower bound; and this is one user on one device, not a sample. But it
> points the same way as the published ~510 m/day estimate, and it means even
> the "heavy user lands 0.8–1.2 km" figure in this document is optimistic
> against the only real data we have. **The landmark table remains correctly
> scaled** — Empire State Building at 443 m is roughly a heavy real day.

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
- [ ] **P4.2b** **Reopened 2026-09-04 — the sweep missed a screen.**
      `LeaderboardScreen.kt:75-80` still declares `groupName = "College Friends"`,
      `mostImprovedName: String? = "Lewis"` and a three-row invented `entries`
      list as default parameter values. `MainShell:522` passes `null` and real
      data, so nothing fabricated reaches a user today — which is word for word
      what was true of `rankPosition = 2` before P4.2a called it a landmine.
      Found by grepping for defaults rather than by the sweep meant to catch
      them. Fix it, then re-run the grep across every `@Composable` with a
      preview, not just the ones already known to have had fake data.

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
