# BUILD_PLAN.md — the only list that decides what happens next

**Created:** 2026-09-14 · **Goal:** 50 people using Scrolla daily, without problems.

This replaces the scattered state of `PREMIUM_CHECKLIST.md`, `SPRINT_LOG.md`,
`AUDIT_A_TRACK.md`, `SENSOR_PROGRESS.md`, `SOCIAL_PROGRESS.md` and
`REVIEW_LOG.md` as the answer to **"what do we do next?"**. Those files stay —
they hold the evidence, the measurements and the history, and this document
points at them rather than repeating them. But when they disagree with this
file about priority, this file wins.

Ownership split between A and B is **not** used here. It was creating two lists
and two queues; there is one product.

**How to use it.** Work top to bottom. Stage 1 before Stage 2, no exceptions —
each stage is defined by what it unblocks, not by how interesting it is.

---

## Where things actually stand

| | |
|---|---|
| Tracking | **Works.** 7 days unbroken on MIUI, 16–20 active hours/day, 7,546 events |
| Accuracy | **Verified.** S0.7 re-run passed; the 31 % inflation is fixed and measured |
| Battery | **0.36 %** (14.1 mAh) — and 82 % of that is network, not tracking |
| Storage | 686 rows/day → **18.2 MB/year** |
| Tests | **153 unit** — incl. 8 Compose screen tests and 6 migration tests |
| Release build | Signed, minified, 4.64 MB, runs — but **cannot sign in** (2.6a) |
| Devices ever tested | **1** |
| Crash visibility | Crashlytics files correct non-fatals, verified on device. **Delivery waits for a process restart** (1.2e) |

Closed since 6 Sep and not yet ticked in the old docs: the full 11-finding
A-track audit, the accuracy re-run, the MIUI root cause, personal-record and
group-record eligibility, the tracking-off banner, the backup, the onboarding
restructure, and the entire release build config.

---

## STAGE 1 — Nothing ships until these are done

Six items. These are not "important"; they are the difference between handing
out an APK and handing out a support burden you cannot answer.

**Five of the six are done (2026-09-14).** 1.1, 1.2 and 1.3 are built and
verified on a device; 1.5 is decided; 1.6 is built and seen. What remains is
**1.4** — one phone that isn't Rohit's, briefed in `TASK_1.4_INSTALL_PATH.md`
— and **1.5b**, one paragraph of onboarding copy.

### 1.1 — Sync in the background ✅ *(built and verified on device 2026-09-14)*

`triggerFirestoreSync()` was called only from `SyncViewModel`, which lives only
while the app is on screen. **A user who tracked all day and didn't open Scrolla
never appeared on their group's leaderboard that day** — `getGroupLeaderboard`
filters on `date == today` and they had no document.

With 50 people the board showed only whoever happened to open the app. The thing
you open the app to see was broken by not opening the app.

- [x] The work moved out of the ViewModel into `SyncEngine` — same three steps,
      no Android lifecycle — so `SyncViewModel` (cadence while someone watches)
      and `SyncWorker` (hourly, app closed) run the *same* code.
- [x] **One engine per process**, held by `ScrollaGraph`. Load-bearing, not
      tidiness: the engine serialises passes against itself, so foreground and
      background can only be stopped from doing identical work if they share
      the instance. Two engines would each behave alone and race each other.
- [x] Verified with Scrolla closed and WhatsApp on screen: today's total
      reached Firestore for both groups. The process was started by the
      accessibility service rebinding, **not by anyone opening the app** —
      exactly the case this exists for. See DEVICE_TEST_LOG.

**One hour, not fifteen minutes.** Foreground is frequent because someone is
looking at the number; background only has to get today's document written, and
an hour stale is invisible on a board that resets daily. Against a battery cost
already measured at 82 % network, 24 passes a day beats 96 for no loss anyone
can perceive. `BACKGROUND_SYNC_INTERVAL_MINUTES` — WorkManager's floor is 15 if
this ever proves wrong, but lower it after a complaint, not a guess.

### 1.2 — Crashlytics 🟡 *(built and proven 2026-09-14; delivery lag open)*

*(was P0.3a/b/c)* Every failure this project has had was found with a USB cable
and 16 MB of logcat. That does not scale past the person holding the phone.
With 50 users, a crash you cannot see is a user who silently stops using it.

And Scrolla barely crashes — it *degrades*. A read throws, the catch returns
0f, and the screen renders a confident zero. That is what these report.

- [x] **1.2a** Crashlytics 19.0.3 via the Firebase BoM. Release uploads its
      mapping file, so reports from a minified build are readable. APK 4.56 MB,
      unchanged. Initialises on device and registers a session.
- [x] **1.2b** Sixteen non-fatals: all twelve Room reads/writes in
      `ScrollRepository`, both sync failures, the batch flush, and the flush
      whose *failure to record the failure* also fails — that last one means
      the health card will not show the outage either.
- [x] **1.2c** Checked on the device, not asserted. The session's `user-data`
      is one field, `{"userId":"…"}` — no display name or email, though both
      are at the call site. Metadata is SDK/app/OS/device only: no distances,
      no package names. `firebase-analytics` is not a dependency, so there are
      no automatic screen-view breadcrumbs or advertising id. Enforced by
      shape: `Where` is an enum, so `nonFatal()` has nowhere to put a package
      name, and a test fails if someone adds a `String` overload.
- [x] **1.2d** **A caught exception does become a report.** Forced one with
      aeroplane mode on 2026-09-14. `triggerFirestoreSync()` timed out and
      wrote a non-fatal carrying the right label, the full stack with line
      numbers, and nothing else — audited against the device's own data: 0 of
      the 42 other-app package names, 0 daily totals. Three fired in all, and
      the first predates the test: the network was already failing, so it
      caught a real one unprompted.

      That was exactly the gap that shipped the reset-guard bug: configured,
      logging, never once checked where it produces a value. Worth the ten
      minutes — it found the item below, which reading the code would not have.

- [ ] **1.2e** **Decide what to do about delivery lag.** 🔴 *(new, 2026-09-14)*

      Reports are written immediately and uploaded only at the **next process
      start**. Scrolla's process is built not to restart: its foreground
      service holds it at `oom_score_adj` 200 (`prcp F/S/FGS`), and `am kill`
      refuses it, since that only reclaims cached processes at adj ≥ 900.
      Measured: seven minutes after the network returned, 3 events pending, 0
      finalised, 0 uploaded.

      So the thing that makes tracking survive is the thing that keeps the
      crash reports on the phone. The argument for 1.2 was "a crash you cannot
      see is a user who silently stops using it" — and a report that arrives
      after they have already given up does not answer it.

      **Measured 2026-09-14: 9.5 hours.** Last night's three non-fatals were
      still on the device this morning, undelivered, in the same session
      directory — the process had lived from 01:48 to 11:12. They uploaded the
      instant an install restarted it. So the path works end to end and the lag
      is simply the process lifetime; on a phone not being rebuilt by a
      developer twice a night it will often be longer.

      Not fatal: reports go up on reboot, an OEM kill, an update, or a real
      crash. **But it should be a decision with a measurement behind it.** The
      cheapest next step is to find out how often a real process restarts over
      a week before building anything.

### 1.3 — Everything runs twice ✅ *(fixed and verified on device 2026-09-14)*

Confirmed on device 2026-09-14: one launch produces **two** `Firestore sync
requested` passes and every record line twice. Harmless at one user; at 50 it
doubles every read, write and the 82 % of battery that is already network.

**The cause was not what this file first recorded.** It said "two
`SyncViewModel` instances are alive". There is one. `MainShell` starts
`runPeriodicSync()`, whose first pass fires immediately, *and* registers
`RefreshOnResume` — and adding a lifecycle observer to an already-RESUMED owner
dispatches `ON_RESUME` at once. Both triggers fire on every launch, ~400 ms
apart, from one instance. The proof is the quiet ticks after it: 01:17:50 and
01:18:06 each produced exactly one pass, and a second ViewModel would have been
running its own fifteen-minute timer.

The same log showed a second bug underneath: `Backup reconcile uploaded 2
day(s)` **twice**, 103 ms apart. `hasReconciled` is a check-then-set across a
suspending read, so two concurrent passes both cleared it and both uploaded the
whole history.

Fixed in `SingleFlightSync` (mutex, so passes cannot overlap) plus
`SyncCadence.shouldRun()` (pure, tested, drops a pass starting within 60 s of
the last). Tests 114 → 131.

- [x] **1.3a** Verified on device, versionCode 177: one `Firestore sync
      requested` at launch, one reconcile upload, one record line per group —
      each of them previously two. The dropped pass logs
      `skipped (periodic) — a pass started 8142 ms ago`. See DEVICE_TEST_LOG.

      Worth keeping: the duplicate trigger fires ~400 ms after the first, but
      the mutex makes it wait for the first pass to *finish* before asking, so
      it arrives 8 s late. A window sized against the 400 ms gap would not have
      caught it.

### 1.4 — The install path, on a phone that isn't Rohit's 🔴

Android 13+ blocks **every** sideloaded app from Accessibility until restricted
settings are unlocked. The onboarding step for it exists but has been seen by
nobody. Tested on one device, one OEM.

- [ ] **1.4a** Full install → onboarding → first number, on a non-Xiaomi phone.
- [ ] **1.4b** Same on a second Xiaomi, to confirm the OEM steps still read true.
- [ ] **1.4c** Write the install instructions someone receives with the APK.

### 1.5 — Tell people about the UPI conflict 🟡 *(decided 2026-09-14)*

*(was P2.13a/b)* Payment apps disable themselves while an accessibility service
is enabled. It is an anti-fraud control working correctly and **must not be
evaded**. Handing 50 people an APK that quietly breaks their payment app is a
trust failure, not a bug report.

- [x] **1.5a Decided: accept and disclose. No pause control.** Rohit's call,
      on the evidence that most payment apps are unaffected — GPay works on his
      device, supermoney does not. Degrading everyone's tracking to serve the
      minority is the wrong trade.

      **And half of the alternative could not have been built anyway.** An app
      can disable its own accessibility service (`disableSelf()`), but it
      cannot re-enable it — that needs `WRITE_SECURE_SETTINGS`, which Android
      withholds precisely so an app cannot switch itself back on around a
      payment. See the note already in `TrackingHealthWatcher`.

      That makes "pause automatically, resume automatically" impossible, and
      **auto-pause without auto-resume is worse than nothing**: tracking would
      silently switch off after every transaction. This project already has
      that exact failure in its history — the multi-day gap after a UPI app
      told the user to turn Scrolla off. Recorded here so it is not proposed
      again.

- [ ] **1.5b** Say it in onboarding, before the Accessibility step. Now the
      only part left, and it is one paragraph of copy — the Privacy screen
      (1.6) already carries the full disclosure.

### 1.6 — Privacy screen ✅ *(built and seen on device 2026-09-14)*

*(was P0.5d)* Mandatory for an app built on an AccessibilityService, and more so
now there is a cloud backup — and, since this morning, crash reporting. The
retention promise used to live in one settings caption.

Written to one rule: **where a fact was available, no promise.** The
load-bearing sentence is the one the user can check without us —
`canRetrieveWindowContent="false"` is in the service config, so Android never
delivers screen content to Scrolla at all, and the screen says where to go and
look. "We do not read your screen" is a promise; "Android does not give it to
us" is not.

Covers what it receives, what it cannot see, what stays on the phone, what
leaves and to whom, crash reports, retention, and how to get everything back or
gone. Payment apps are named here too rather than left to be discovered
mid-transaction.

Reached from Settings, above the Export / Clear / Delete ladder — it explains
what those three act on, and being read-only it breaks what was a run of three
destructive rows.

Rendering checked on device. Rohit: fine for now, with a UI/UX pass expected
later.

---

## STAGE 2 — Needed for 50 people, not for 5

### 2.1 — How do 50 people get updates?

No Play Store, no auto-update. Every fix means 50 manual sideloads. This is
unsolved and it is a bigger operational problem than any single bug on this
list.

**The accessibility-grant half of this may be wrong.** It has been recorded as
"each reinstall clears the grant, confirmed twice". On 2026-09-14 an
`adb install -r` over the existing debug build left it intact — and *bound*,
checked with `dumpsys accessibility` rather than trusting the settings flag.
It kept happening — **four consecutive reinstalls on 2026-09-14 left the grant
intact and the service bound**.

**There is now a candidate explanation for the original rule.** Read
`accessibility_enabled` immediately after `adb install -r` and it returns `0`;
read it fifteen seconds later and it is `1`, with the service bound. The
package replace takes the process down and the system re-registers the service
shortly after. A check run straight after an install would report the grant
gone every time, and that is exactly how anyone would check it. Unproven, but
it fits four failures to reproduce, and it is cheap to test deliberately. Both were
same-key debug updates, which may be the distinguishing factor — neither
changed the signing key nor uninstalled first, and that is the obvious thing to
test next. But it is no longer a single anomaly, and this item is priced on the
old assumption: "all 50 people re-enable Accessibility after every update" and
"nobody has to" are different problems with different answers.

- [ ] **2.1a** Decide the channel: hosted APK + in-app "update available", or
      Play internal testing, or accept manual distribution and write it down.
- [ ] **2.1b** Warn in-app that an update needs Accessibility re-enabled.

### 2.2 — Firebase budget alert

*(was S3.13)* Free, five minutes, prevents a surprise bill. Do it before the
user count moves.

### 2.3 — The screens nobody has ever run 🟡 *(empty states covered 2026-09-14)*

*(was P2.2a/b)* Personal Records, App Breakdown, Weekly Recap, Hall of Fame and
Settings have never been opened on a device with real data — **and no screen has
ever been seen in its empty state**, which is where the last three UI bugs were.

- [x] **Empty states are now tested** rather than looked at: eight Compose
      tests in `EmptyStateTest`, run on every build. Two of them assert the
      specific trap — that a screen with no data shows neither "July 12" nor
      "Instagram", the preview defaults that render plausibly enough to pass
      for a real user's history.
- [x] Checked statically while writing them: all four call sites in `MainShell`
      pass every argument, so no screen is currently falling back to a preview
      default. That was the P4.2 failure and it is not present.
- [ ] **Still open: the populated path on a device with real data.** Untested,
      and it needs a person to tap through — adb cannot drive the UI on MIUI.

### 2.4 — A second and third device

*(was S3.14, S0.8)* One device and one OEM is not a test matrix. Includes
re-running the accuracy test on a second phone.

### 2.5 — Instrumented tests: currently zero 🟡 *(screens covered 2026-09-14)*

*(was P0.1e)* 114 unit tests covered pure logic. Nothing covered navigation,
Room against a real database, or a Compose screen rendering. You cannot
hand-test every screen before each of 50 sideloads.

- [x] **Compose screen rendering is covered** — see 2.3. It found a launch
      crash on its first run: `SyncWorker.schedule()` called
      `WorkManager.getInstance()` unguarded from `Application.onCreate()`,
      where a throw is not a failed feature but an app that will not start.
      Added the day before, found by a test that was not looking for it.
- [x] Stale test dependencies replaced: `androidx.test.ext:junit` 1.1.5 → 1.2.1
      and Espresso 3.5.1 → 3.6.1. They had been declared for months with no
      `androidTest` directory to use them.
- [x] **Room migrations are covered** — six tests taking a version 1 database
      through the whole chain to version 4, run against a real SQLite file
      using `ScrollaDatabase.ALL_MIGRATIONS`, the same array `getDatabase()`
      ships. A migration written and never registered now fails a test instead
      of refusing to open a user's database on the first launch after an
      update. Verified by breaking the index name on purpose and watching it
      fail; a green test nobody has seen fail is not evidence.
- [x] **Room schema export now actually works.** `0d338ab` is titled "export
      Room schema JSON for migration validation" and never added
      `room.schemaLocation` — it committed `1.json` once and nothing
      regenerated it through versions 2, 3 or 4, while every build warned. The
      new `4.json`'s identity hash matches `room_master_table` on the device,
      so the generated schema is confirmed against what people are running.
      **Every future schema change must commit its JSON alongside.**
- [ ] **Two things the migration work turned up, neither acted on:**
      *(a)* `MIGRATION_2_3`'s comment says a wrong index name throws on first
      launch. It did not — Room opened fine and only the index assertion
      caught it, which would make the real consequence a *silently* missing
      index and every `scroll_events` read a full scan. Worth confirming on a
      device; Robolectric's SQLite is not the phone's.
      *(b)* Versions 2 and 3 have no exported schema and cannot get one. The
      chain is tested end to end, so this costs nothing today, but a future
      `MigrationTestHelper` run cannot start from either.
- [ ] **Navigation.** Nothing covers the `routeStack` in `MainShell`.

**On Robolectric vs a device.** These run on the JVM. The only system image on
this machine is API 37, where Espresso still calls the removed
`InputManager.getInstance()` and every test dies before asserting — upgrading
Espresso does not fix it. Running on the JVM also puts them in the ordinary
`test` task, so they run on every build rather than when someone remembers to
attach a device. A genuine on-device run still needs an API ≤ 36 image
downloaded.

### 2.6 — Release build 🔴 **LAUNCH BLOCKER FOUND** *(2026-09-14)*

The signed-in paths — Room reads, Firestore sync, group flows — are all behind
Google sign-in and had never run minified.

**The release build cannot sign in.** `Sign-in failed: Google sign-in failed
(code 10)` — `DEVELOPER_ERROR`, the signing certificate is not registered
against the OAuth client. Only the **debug** SHA-1 is in
`google-services.json`:

| | SHA-1 | registered |
|---|---|---|
| debug | `B0:A6:93:…:8E:CF:95:E7` | yes |
| **release** | `7B:D4:DA:E0:C0:84:EA:47:E1:18:61:B5:7B:19:93:15:FA:5C:B6:43` | **no** |

Everything is behind sign-in, so the APK meant for fifty people is unusable —
a button that fails and no way past it. It has read as "release build works"
since `aaa6003` because that verified compile, sign, install and launch.
Nobody had pressed the button.

- [ ] **2.6a** 🔴 Add the release SHA-1 in Firebase Project Settings → Android
      app → Add fingerprint. **Rohit only — console access.** Then download the
      regenerated `google-services.json` (it gains a second `oauth_client`) and
      commit it. No app code changes.
- [ ] **2.6b** Re-run sign-in on the release build and confirm code 10 is gone.
- [ ] **2.6c** Then the rest of what 2.6 was for: Firestore sync, Room reads
      through the UI, and group flows, all minified.

**Everything else about the release build is verified** (2026-09-14, emulator,
`flags=0x0`): launches, Compose renders, accessibility service binds, scroll
measured at `deltaCm=4.09575`, `BatchFlush: Successfully flushed batch`,
Crashlytics activated, and a sweep of the whole log for `ClassNotFoundException`
/ `NoSuchMethodError` / `VerifyError` and friends found nothing. R8 is not the
problem; the certificate is.

**Until 2.6a is done, do not hand out the release APK.** The debug build is
unaffected and is what 1.4 should use.

### 2.7 — Offline is not failure

*(was P2.3d)* Nothing checks connectivity, so being on the train reads as an
error. With 50 users this generates support messages that aren't bugs.

---

## STAGE 3 — Makes it good, not viable

Ordered by value to a daily habit.

- [ ] **3.1 Widgets** *(S2.6, S3.9)* — small, medium, large. The largest single
      build left, and the only **passive** surface: the thing that makes a
      tracker a daily habit rather than an app you remember to open. Arguably
      belongs in Stage 2 given 1.1.
- [ ] **3.2 Weekly recap share card** *(S3.5)* — the data is wired; the
      shareable *image* does not exist. This is the only organic growth loop
      in the product.
- [ ] **3.3 Most-improved recognition** *(S3.11)* — on a reverse leaderboard the
      person in last place needs something they can win.
- [ ] **3.4 App Breakdown's "cut X by 20 %" nudge** *(S3.2)* — turns a list into
      a suggestion.
- [ ] **3.5 Group edge cases** *(S3.12)* — group of one, empty group, member who
      never syncs.
- [ ] **3.6 Orphaned groups** *(P2.7f)* — a group whose last member leaves stays
      in Firestore forever, unreachable. Already real: four group documents
      exist, two are abandoned.
- [ ] **3.7 Accessibility pass** *(P3.3a–e)* — TalkBack, 48dp targets, 200 % font
      scale, contrast. An app built on an accessibility API should survive one.
- [ ] **3.8 Phone number linking** *(S1.B4)* — Settings advertises it as not yet
      available, which is at least honest.
- [ ] **3.9 Primary group** *(P3.5a)* — build it or cut the machinery.

---

## STAGE 4 — Explicitly not doing before launch

Written down so they stop being reconsidered every week.

| Item | Why not |
|---|---|
| **i18n / `strings.xml` migration** *(P3.1a–e)* | 50 English-speaking friends in one country. Real work, zero benefit to this launch. |
| **RTL** *(P3.1e)* | Same. |
| **Landscape** *(P3.4a)* | A scroll tracker is used in portrait. Cut unless someone reports it. |
| **`degradedReason` column split** *(P2.4e)* | Now only two writers, since reads stopped writing health. Cosmetic until it isn't. |
| **Per-day tracking health** *(P2.6e / P2.11b)* | The real fix for record eligibility, but a new table and a migration. The span rule is good enough for a test group. |
| **Excluding Scrolla's own scrolling** *(P2.10a)* | Real but tiny — the app is open for seconds a day. |
| **Onboarding number accuracy** *(P4.1a–d)* | The 2,800× claim is 3–7× too high and the fake leaderboard is hardcoded. Fix with the share card (3.2), same copy pass. |

---

## Known limitations we are choosing to live with

Not bugs to be rediscovered — decisions, with reasons.

1. **A hole in the middle of a day still sets records.** Span sees coverage, not
   contiguity, so 25 Aug (9.5 h missing after a crash) qualifies. Worth ~2 m
   against 106 m. Real fix is per-day health.
2. **Gap detection can't tell a dead tracker from a quiet day.** Waking hours
   are a proxy. A night shift gets told wrongly. Fails toward silence.
3. **Per-app history never leaves the device** — so it cannot be restored from
   our servers. Android Auto Backup covers it into the user's own Drive.
   *Rohit has approved changing this if Auto Backup proves unreliable.*
4. **`scroll_events` is kept indefinitely**, now measured at 18.2 MB/year — up
   from the 6.7–11 MB estimated on 6 Sep, which was taken from days when
   tracking was broken. Revisit before real users; the Clear action exists.
5. **Reinstalling clears the accessibility grant.** Confirmed twice. Affects
   every update (see 2.1).
6. **`getPersonalBestKm()` / `getPersonalBestDay()` have zero callers** —
   superseded by `PersonalBest`. Delete or keep; a raw minimum is a loaded gun
   on a lowest-wins board.

---

## Open reviews

- **REVIEW_LOG #10** — the `users/{uid}/dailyTotals` rules are **live and
  unreviewed**. Reviews #2 and #4 each caught a real bug; #4 found that group
  joining had never worked at all. Worth Monish's eyes even with the A/B split
  dropped.
- **REVIEW_LOG #8** stays "changes required" until whoever reviews closes it.

---

## What the old documents are for now

| File | Still use it for |
|---|---|
| `PREMIUM_CHECKLIST.md` | The evidence behind each finding — measurements, device output, reasoning. Not the priority order. |
| `SPRINT_LOG.md` | History, and the milestone wording for anything in Stage 3. |
| `AUDIT_A_TRACK.md` | Closed. All 11 findings resolved; keep as the record of how they were found. |
| `DEVICE_TEST_LOG.md` | Keep writing device sessions here. |
| `SENSOR_PROGRESS.md` | The corrected accuracy figures. |
| `DATA_CONTRACT.md` | Still the schema and retention source of truth. |
| `REVIEW_LOG.md` | Still the review record. |
