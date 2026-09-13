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
| Tests | **136 unit, 0 instrumented** |
| Release build | Signed, minified, 4.6 MB, **runs** — verified on an emulator |
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

### 1.1 — Sync in the background 🔴

`triggerFirestoreSync()` is called only from `SyncViewModel`, which lives only
while the app is on screen. **A user who tracks all day and doesn't open Scrolla
never appears on their group's leaderboard that day** — `getGroupLeaderboard`
filters on `date == today` and they have no document.

With 50 people the board shows only whoever happened to open the app. The thing
you open the app to see is broken by not opening the app.

Needs a periodic `WorkManager` job that calls the same sync. Not config — real
work. **This is the single largest gap and it appears in none of the old docs.**

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

      Not fatal: reports still go up on reboot, an OEM kill, an update, or a
      real crash, and MIUI is aggressive enough that in practice this is
      probably a day rather than a week. **But it should be a decision with a
      measurement behind it, not an assumption.** The cheapest next step is to
      find out how often a real process actually restarts over a week before
      building anything.

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

### 1.5 — Tell people about the UPI conflict 🔴

*(was P2.13a/b)* Payment apps disable themselves while an accessibility service
is enabled. It is an anti-fraud control working correctly and **must not be
evaded**. Handing 50 people an APK that quietly breaks their payment app is a
trust failure, not a bug report.

- [ ] **1.5a** Decide the product answer: accept and disclose, or ship a
      one-tap "pause tracking" for when they need to pay.
- [ ] **1.5b** Say it in onboarding, before the Accessibility step.

### 1.6 — Privacy screen 🔴

*(was P0.5d)* Mandatory for an app built on an AccessibilityService, and more so
now there is a cloud backup. The retention promise currently lives in one
settings caption.

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
One observation against two, so the rule stands until re-tested, but it is
worth two deliberate tries: "all 50 people re-enable Accessibility after every
update" and "nobody has to" are different problems with different answers.

- [ ] **2.1a** Decide the channel: hosted APK + in-app "update available", or
      Play internal testing, or accept manual distribution and write it down.
- [ ] **2.1b** Warn in-app that an update needs Accessibility re-enabled.

### 2.2 — Firebase budget alert

*(was S3.13)* Free, five minutes, prevents a surprise bill. Do it before the
user count moves.

### 2.3 — The screens nobody has ever run

*(was P2.2a/b)* Personal Records, App Breakdown, Weekly Recap, Hall of Fame and
Settings have never been opened on a device with real data — **and no screen has
ever been seen in its empty state**, which is where the last three UI bugs were.

### 2.4 — A second and third device

*(was S3.14, S0.8)* One device and one OEM is not a test matrix. Includes
re-running the accuracy test on a second phone.

### 2.5 — Instrumented tests: currently zero

*(was P0.1e)* 114 unit tests cover pure logic. Nothing covers navigation, Room
against a real database, or a Compose screen rendering. You cannot hand-test
every screen before each of 50 sideloads.

### 2.6 — Release build on a real device

The signed-in paths — Room reads, Firestore sync, group flows — are all behind
Google sign-in and have never run minified. Needs one phone on a release build.

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
