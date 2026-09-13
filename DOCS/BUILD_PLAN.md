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
| Tests | **114 unit, 0 instrumented** |
| Release build | Signed, minified, 4.6 MB, **runs** — verified on an emulator |
| Devices ever tested | **1** |
| Crash visibility | **None** |

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

### 1.2 — Crashlytics 🔴

*(was P0.3a/b/c)* Every failure this project has had was found with a USB cable
and 16 MB of logcat. That does not scale past the person holding the phone.
With 50 users, a crash you cannot see is a user who silently stops using it.

- [ ] **1.2a** Add Crashlytics.
- [ ] **1.2b** Non-fatal reports on the paths that currently swallow errors —
      every `catch` in `ScrollRepository`, the flush path, the sync path.
- [ ] **1.2c** Confirm no scroll content, package list or identity beyond the
      uid is attached. *(was P0.3c/P1.2b)*

### 1.3 — Everything runs twice 🔴

Confirmed on device 2026-09-14: one launch produces **two** `Firestore sync
requested` passes and every record line twice. Two `SyncViewModel` instances are
alive. Harmless at one user; at 50 it doubles every read, write and the 82 % of
battery that is already network.

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

No Play Store, no auto-update. Every fix means 50 manual sideloads, **and each
reinstall clears the accessibility grant** (confirmed twice) so each person must
re-enable it. This is unsolved and it is a bigger operational problem than any
single bug on this list.

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
