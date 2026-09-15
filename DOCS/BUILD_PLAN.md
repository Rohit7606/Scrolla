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
| Tests | **155 unit** — incl. 8 Compose screen tests and 6 migration tests |
| Release build | Signed, minified, 4.64 MB — **sign-in, sync and group flows all verified** |
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

### 1.4 — The install path, on a phone that isn't Rohit's ✅ *(2026-09-14)*

Android 13+ blocks **every** sideloaded app from Accessibility until restricted
settings are unlocked. The onboarding step for it existed and had been seen by
nobody.

**It works.** Monish ran a full install and onboarding on an **OPPO CPH2565,
Android 15, ColorOS 15** — the first non-Xiaomi device Scrolla has run on, and
the first person who did not build it going through onboarding.

- [x] **1.4a** Full install → onboarding → first number, unaided. No source
      reading, no adb workarounds, no questions. **The restricted-settings
      three-tap instructions, written for MIUI, matched ColorOS exactly** —
      that was the biggest unknown in this plan. Battery instructions matched
      too. 12 events across Reddit, LinkedIn and Instagram, correctly
      separated, correct local date. Service enabled *and* bound,
      `Crashed services:{}`. **No app bugs.**
- [ ] **1.4b** Second Xiaomi — still not done, and lower value now that a
      second OEM has passed.
- [ ] **1.4c** Install instructions to ship with the APK.

**Three tooling findings, all real, all fixed or recorded:** the backup command
corrupts the file on Windows PowerShell (measured: 655,360 → 768,060 bytes,
`file is not a database`); `run-as` does not work on release builds, so a phone
carrying one cannot be backed up before a key change forces an uninstall; and
there is no `sqlite3` on device.

**Not settled:** the Today screen was never screenshotted, so the number
reaching the *UI* on that device is unconfirmed — the database is proven, the
screen is not. And two OEMs is still two; nothing here speaks to Samsung, the
likeliest OEM among fifty people.

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

**The accessibility-grant half of this was wrong, and is now settled.**
"Each reinstall clears the grant, confirmed twice" does not reproduce. Tested
deliberately on 2026-09-14 by polling once a second through a same-key debug
update:

```
BEFORE          enabled=1  bound=1
adb install -r  Success
t+1s .. t+5s    enabled=0  bound=0
t+6s            enabled=1  bound=0
t+8s onward     enabled=1  bound=1
```

The grant is never cleared. There is a ~5 second window where it reads 0 and
~8 seconds before the service rebinds. **Checking immediately after an install
— which is what anyone does — reports the grant gone on a device where it was
never gone.** That window is the entire origin of the rule. Five consecutive
reinstalls plus this trace, against a note saying "confirmed twice".

**So an ordinary update costs the user nothing**: no re-enabling, no data loss,
~8 seconds of interruption. This item was priced on the opposite and is much
cheaper than it looked.

**The exception is a signing-key change**, which forces an uninstall and takes
everything. Monish hit exactly that on the OPPO device —
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and no backup possible because `run-as`
does not work on the release build already installed. That is the real risk,
and it is narrower and sharper than "every update".

Scope: same key, debug over debug, one device. Release-over-release has never
been done.

- [ ] **2.1a** Decide the channel. **Play Store is ruled out** (2026-09-14,
      Rohit: the $25 Play Console fee is not affordable and the app is not
      going to the Play Store). Recommendation below uses only free tools.

      **Primary: Firebase App Distribution.** Verified on the Firebase pricing
      page 2026-09-14 — listed **"No-cost" on the Spark plan, with no usage
      limits stated**. The project already exists, so this costs nothing and
      adds no new account.

      What it gives that a shared APK link does not: tester groups, email
      invites, **update notifications on the tester's device**, and a record of
      who is on which build — which matters at fifty people, because
      "it's broken" is unanswerable without knowing what they are running.

      **Be clear about what it does not fix.** An app installed this way is
      still sideloaded, so **Android 13+ restricted settings still applies**.
      That was the argument for Play, and it is now much weaker: 1.4 proved the
      onboarding screen walks a stranger through that block successfully on a
      second OEM. It is handled, not avoided.

      **Secondary, and the bulletproofing: GitHub Releases + Obtainium.**
      [Obtainium](https://github.com/ImranR98/Obtainium) is an open-source
      Android app that installs and updates apps straight from a GitHub
      Releases page and notifies on new releases. Testers install it once,
      point it at the release repo, and updates arrive without anyone being
      chased. Free, no accounts beyond GitHub, and no dependence on Firebase
      continuing to offer this for free.

      Note the repo would have to be reachable by testers. **Use a separate
      public repo holding only the APK**, not this one — publishing the source
      is a different decision, and this repo's history is not written for an
      audience.

      Two channels rather than one is deliberate: App Distribution is the
      managed path that tells you who has what, Obtainium is the one that keeps
      working if that ever changes.

      - [x] **2.1a-i** App Distribution wired and provisioned. Group
            `scrolla-testers` exists with Rohit and Monish in it.
      - [x] **2.1a-ii** `Rohit7606/scrolla-releases` created (public, APK only)
            and **v1.0.203 shipped to both channels on 2026-09-14**.
      - [ ] **2.1a-iii** The one-page install note (1.4c), covering both routes
            and the restricted-settings step.

      **First release verified three ways**, because "the script said it worked"
      is not evidence: App Distribution reports release count 1 against 2
      testers; the GitHub release is published, not a draft, with a 4.64 MB
      asset; and the SHA-256 of the local build, the GitHub download and the
      binary hash in Firebase's own URL are all `c6138ebe…`. The same bytes are
      on both channels.

      Setup is in `DOCS/RUNBOOK_DISTRIBUTION.md`; releasing is
      `./scripts/release.sh`.

- [x] **2.1b Dropped.** An update does not need Accessibility re-enabled —
      see above. Shipping that warning would have been a false alarm in the
      app, teaching people to ignore a message that is wrong.
- [ ] **2.1c** **Warn before a signing-key change instead**, which is the case
      that genuinely destroys data. Anyone holding a build signed with a
      different key must export before updating, and `run-as` cannot rescue
      them afterwards. This has already happened once, to Monish.

### 2.2 — Firebase budget alert ✅ *(closed 2026-09-14 — Spark, nothing to cap)*

*(was S3.13)* Checked properly on 2026-09-14, and the item is not quite what it
said.

**Step 1 — find out which plan the project is on.** Firebase console →
`scrolla-9986e` → the plan is shown at the bottom of the left sidebar, or via
⚙ → **Usage and billing** → **Details & settings**.

**If Spark (no-cost), which is the likely answer:**

There is no billing account attached, so **a surprise bill is not possible**.
Services stop serving when a quota is reached instead of charging. There is
nothing to cap, and the Cloud budget screen will not apply.

What matters instead is the ceiling. Verified on the Firebase pricing page
2026-09-14:

| Firestore, Spark | limit |
|---|---|
| document reads | 50,000/day |
| document writes | 20,000/day |
| document deletes | 20,000/day |
| stored data | 1 GiB |

Against that, background sync at one pass an hour with fifty users in two
groups each is roughly **50 × 24 × 2 ≈ 2,400 writes/day** — about 12 % of the
write budget. Reads are the side that grows with group size, since each
leaderboard view reads every member's daily total. Watch it at
**Firestore → Usage**.

Crashlytics and App Distribution are both no-cost on Spark too, so neither
1.2 nor 2.1a changes this.

**If Blaze (pay as you go):**

Google Cloud console → **Billing** → **Budgets & alerts** → **Create budget**,
scope it to `scrolla-9986e`, set an amount, and set thresholds at 50/90/100 %.

**Two things worth knowing before relying on it.** A budget alert **does not
pause anything** — it emails you while the bill keeps growing. And Google's
*spend cap* budgets, which do pause a service, **are only available for AI
Logic, App Hosting, Cloud Functions and Extensions — not Firestore**. So on
Blaze there is no way to hard-cap the thing Scrolla actually uses.

**Which means Spark is the safer plan for this specific risk**, and staying on
it is a real answer rather than a default. Only move to Blaze for a feature
that needs it, and know that the ceiling becomes a notification rather than a
limit when you do.

- [x] **2.2a** Checked: **Spark**. No billing account, so a surprise bill is
      not possible and there is nothing to cap. Confirmed by Rohit 2026-09-14.
- [x] **2.2b** Closed. Crashlytics (1.2) and App Distribution (2.1a) are both
      no-cost on Spark, so nothing built this week moves the project off it.
      The number to watch is **Firestore reads**, not money: measured from the
      code, a sync pass costs **4 reads and 1 write per group the user is in**,
      so fifty people in two groups each at hourly background sync is ~9,600
      reads and ~2,400 writes a day, plus leaderboard views. Roughly 29 % of
      reads and 12 % of writes. **Superseded by 2.2c (2026-09-14): a pass now
      costs 2 reads per group, so the same fifty people cost ~4,800 reads a day
      — about 15 %.**
- [x] **2.2c** ✅ *(2026-09-14)* Fixed, and it was **half** the sync read load,
      not a quarter.

      `SyncEngine.sync()` now reads `users/{uid}/groups` **once** and hands the
      result to both halves. Reading it there rather than inside either half is
      what stops a third step quietly adding a third read.

      The second saving was not in the item. `updateGroupRecords` called
      `getUserGroups`, which reads the memberships **and then reads
      `groups/{id}` for every one of them** to join on a `groupName` and a
      `memberCount` — neither of which that function ever touches. It needs a
      group id and a `joinedAt`, and both are on the membership. So
      `getMemberships()` returns `GroupMembershipRef` with no group documents
      read at all; `getUserGroups()` is unchanged and still what the *screens*
      call, because they do need the name and the count.

      **Per group, per pass: 4 reads → 2** (the membership, and the record
      document `updateGroupRecordIfBetter` compares against). At the 2.2b
      sizing, fifty people in two groups each drops from ~9,600 reads a day to
      ~4,800 — roughly 29 % of the free tier to 15 %.

      `getMemberships` returns `Result`, and `SyncEngine` treats a failure as a
      skipped pass rather than an empty list. An empty list would have uploaded
      today's total to no groups, logged "synced 0 group(s)" and marked the pass
      a success — leaving the user silently off every board, which is the exact
      bug `SyncWorker` was built to prevent.

      Verified on device: `triggerFirestoreSync() successfully synced 2
      group(s)`, then both record offers evaluated against the right join dates
      — `Offering 2026-09-09 to GUW4YE, joined 2026-09-07` and `to S1RK8Q,
      joined 2026-08-23`. The per-group `joinedAt` bound is the thing this
      refactor could most easily have broken, and it did not.

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

### 2.6 — Release build ✅ *(blocker found and fixed 2026-09-14)*

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

- [x] **2.6a** Release SHA-1 registered; `google-services.json` regenerated and
      committed. No app code changed.
- [x] **2.6b** Sign-in works. Code 10 gone.
- [x] **2.6c** **All of it, minified, on a separate test account** so none of
      Rohit's data was touched: sign-in, the Firestore daily-total write, the
      personal backup reconcile, group creation (`6NPBWY`), the leaderboard
      read, and the `joinedAt` record bound from `0b55803`
      (`No record-eligible day for 6NPBWY since joining 2026-09-14`).
      Home, Group, Your-groups and Profile all render minified.

**Two bugs found by running it** — see 2.3, this is its whole argument:
*(a)* Profile read **"1 groups · <name> on widget"**. Hardcoded plural, and it
named the widget surface that does not exist (3.1). Fixed.
*(b)* A returning user on a fresh install goes straight to Home and never sees
onboarding — `isNewUser` is false, so the Accessibility step is skipped and
`is_first_launch` is set to false. The tracking-off banner still catches them,
so it is not silent, but it is worth confirming deliberately. **Not fixed.**

**Everything else about the release build is verified** (2026-09-14, emulator,
`flags=0x0`): launches, Compose renders, accessibility service binds, scroll
measured at `deltaCm=4.09575`, `BatchFlush: Successfully flushed batch`,
Crashlytics activated, and a sweep of the whole log for `ClassNotFoundException`
/ `NoSuchMethodError` / `VerifyError` and friends found nothing. R8 is not the
problem; the certificate is.

The APK is safe to hand out again as of 2026-09-14. **Any future signing-key
change needs its SHA-1 registered too, or this recurs identically.**

### 2.7 — Offline is not failure ✅ *(closed 2026-09-14 — and it was not this)*

*(was P2.3d)* The item said nothing checks connectivity, so being on the train
reads as an error, generating support messages that are not bugs.

**Tested on a device, and the board does not error offline.** With no network
at all, the Group tab rendered normally — "1 of 1 synced today", the stat row,
the solo-group empty state. Firestore's local persistence is on by default and
nothing here disables it, so reads fall back to cache; the reconcile logged
`already in sync (1 local, 1 cloud)` with the network down. `ERROR_NO_CONNECTION`
has never had a caller because that path does not fail.

**The real defect was one clause away and much worse.** `triggerFirestoreSync()`
wrote its failure into `degradedReason`, which the Settings health card turns
into *"Tracking may be limited — Scrolla is enabled but hasn't received recent
scroll events — this may be a battery restriction"*, with a button to check
battery settings. So **no signal told the user their tracking was broken**, gave
a false reason, and sent them to the wrong screen.

That is A6 with a different caller. A6 stopped reads doing exactly this and
warned that another failure kind would need its own column rather than a third
writer on `degradedReason`.

- [x] Deleted `markSyncFailed`, so the write is impossible at compile time
      rather than merely absent. A failed sync stays visible where it should
      be: `lastFirestoreSyncTimestamp` stops advancing and the card renders it
      as "Last synced …", and Crashlytics gets a non-fatal.
- [x] `ServiceHealthWritersTest` names the legitimate writers, so a third
      subsystem reaching for that field fails in CI instead of on a phone.
- [x] **2.7a** ✅ *(2026-09-14)* The board says when it is cached.
      `getGroupLeaderboard` now returns `GroupLeaderboard(totals, isFromCache)`
      rather than a bare list, so the freshness flag travels with the rows it
      describes and a caller has to decide about it instead of not knowing there
      was anything to decide. The Group tab prints "Saved results — couldn't
      reach the server" directly under "N of M synced today", which is the
      sentence that stops being true. The widget drops the flag on purpose: no
      room to say it and no way to act on it.

      **`ERROR_NO_CONNECTION` was still not the answer.** The board does not
      fail offline, so there is no error to report — the defect was a true
      sentence with a missing qualifier, not a failure.

      Verified on device with `svc wifi disable && svc data disable`
      ("Active default network: none"):
      `GroupRepository: Leaderboard served from CACHE for GUW4YE on 2026-09-14
      (1 rows)`, and no such line on the same read with the radios on. That log
      stays: 2.7a rests entirely on Firestore setting that flag on real
      hardware, which is a claim about Firestore, not about this code.
      `CachedBoardTest` covers the half that has to keep being true — caption
      shown when cached, rows still rendered, nothing shown when fresh.

### 2.8 — Knowing when it breaks for someone else ✅ *(2026-09-15)*

*(new)* Service health was written to Room and **never left the device** — not
Firestore, not Crashlytics. Crashlytics catches crashes, and the characteristic
Scrolla failure is not a crash: an OEM kills the accessibility service, the app
still opens, still shows a number, and quietly stops measuring. The only person
who could ever find out was the one least equipped to, and they would just drift
away. At forty people across eight manufacturers that is the likeliest way this
fails.

`TrackingGap` already detected it correctly, counting waking hours so it does not
fire every morning. Nothing reported it. `TrackingTelemetry` now does, from the
**sync pass rather than the UI** — the in-app card can only reach someone who
opened the app, and the person whose tracking died has no reason to.
`SyncWorker` runs hourly regardless.

Two outputs, two questions: a `tracking` map on `users/{uid}` says *who* is dark
and since when (Console-readable, no rules change — that document already allows
the owner to write any field), and a Crashlytics non-fatal says *how often and on
what*, grouped by device and Android version for free.

Deduped on the outage's own `lastEventTimestamp`, which needs no storage: during
an outage nothing is recorded, so that value physically cannot move, and one
outage is exactly one report.

**Disclosed on the privacy screen** as a third "what leaves this phone" item.
Adding a field to what the app uploads without saying so would be the same
failure as the tracking itself — true, invisible, and nobody's choice but ours.

**It caught a real outage on its first run.** The snapshot reported
`accessibility=false running=false`; `settings get secure accessibility_enabled`
returned 0 and `dumpsys` showed `Bound services:{}`. Repeated reinstalls during
this session had knocked the grant off and it had not come back — Rohit's phone
had stopped recording and nothing else would have said so.

---

## STAGE 3 — Makes it good, not viable

Ordered by value to a daily habit.

- [x] **3.1 Widgets** ✅ *(2026-09-14, seen on the home screen)* All three
      sizes, refreshed by AlarmManager and after every batch flush. The only
      **passive** surface in the product, and the one that makes a tracker a
      habit rather than an app you remember to open.

      Three defects, all invisible until a real home screen: `android.view.View`
      is not on the RemoteViews allowed-class list, so medium and large rendered
      as "Can't load widget" while the provider logged success; a `layout_weight`
      on the baseline row sent the unit to the bottom of its band and stranded
      the "m" under the number; and both larger sizes hid their group column
      when there was no honest board — the state every new user is in — leaving
      most of the card empty. The medium now shows the week instead, the large
      gained the week as three totals, and the stranded hairline got an id so it
      can hide with the column it separates.

      Typeface corrected 2026-09-15: all three layouts were using Inter, a third
      face in a two-face system, retired from the app but reintroduced here.
- [x] **3.2 Weekly recap share card** ✅ *(2026-09-15)* `RecapCard` draws a
      1080x1350 PNG on a Canvas, writes it to `cacheDir/share`, and shares it as
      an `image/png` with the caption. Canvas and not a Compose capture: a
      captured screen carries the viewer's theme, font scale and cutout into the
      image, and a share card has to look the same off every device.

      **It is the recap screen, frozen** — same centred column, same 15% coral
      glow, same figure with the unit below it. Rohit pointed at the screen
      mid-build; the first version was a left-aligned design of my own and was
      the wrong answer to a question that already had one.

      **Then the design system corrected the screen.** `References/Scrolla
      Design System.pdf` says section labels are "never the accent — coral
      section labels were the original accent leak", and coral belongs to four
      things, one of which is the hero figure. `WeeklyRecapScreen` had it
      inverted: coral on the label, plain text on the figure. Both are fixed,
      the screen and the card.

      Other corrections from the same source: ground is `#121110`, not the card
      colour a rung up; the landmark line is Instrument Serif **italic**, being
      the one lyrical line a screen is allowed; the wordmark is serif italic
      because no lettering asset exists.

      One knowing exception, written into the code rather than smuggled: a
      dither at alpha 8/255 under the glow. "No grain" is a rule and this is
      grain — but a 38-step alpha ramp across 675 px bands visibly in 8-bit
      colour, and the rule exists to keep grounds flat. One line to delete if
      Rohit would rather have the banding.

      **Verified end to end on 2026-09-15** — Rohit tapped Share and the card
      reached WhatsApp with its caption: FileProvider grant, chooser, image and
      text all work on hardware.

      That screenshot also caught a bug no test had: the card read **"that's
      about 1 km walk"**. The landmark labels are written to follow "≈ ", where
      articles are dropped, and the recap pastes the same string into a
      sentence. Third time that seam has given way — the widget once rendered
      "about the height of Eiffel Tower height". Each landmark now carries both
      a label and a phrase, with one shared honesty guard, and
      `LandmarkPhraseTest` asserts the composed sentences. `RecapCardRenderTest`
      renders through the real pipeline now instead of a hand-written
      placeholder that was grammatical when the product was not.
- [x] **3.3 Most-improved recognition** ✅ *(2026-09-15)* The Group tab names
      whoever cut the most this week, in jade with `trending_down`, exactly as
      the design system draws it. The UI slot already existed and was wired to
      `null`; the work was computing it honestly and for free.

      **It costs no Firestore reads.** Each device writes its own `last7Km` and
      `prev7Km` into the daily-total document it already writes every sync, so
      reading the board reads everybody's. The obvious implementation — query
      fourteen days per member per board view — would have cost ~14N reads a
      view and undone 2.2c several times over.

      No rules change: `groups/{g}/dailyTotals` validates the doc id prefix and
      `userId` and does not whitelist keys, unlike `users/{u}/dailyTotals` which
      does. REVIEW_LOG #10 is untouched.

      `mostImprovedOf` declines rather than guesses, in five ways, each of which
      is a way of inventing a result — fewer than two members with both weeks; a
      member with no previous week (**absent is not zero**, or someone who joined
      on Friday becomes the group's worst backslider); a zero baseline; nobody
      down by more than 100 m; a tie at the top. Nine tests, eight of them
      asserting a refusal.

      **It will stay silent for a while, and that is correct.** The fields are
      absent from every document written before today and from any member on an
      older build, so the award needs two people on the current build with two
      weeks of history behind them. Monish's build is old — expect nothing on
      the board until he updates.

      Not verified on device: the phone was disconnected when this was written,
      so the Firestore write has not been seen on hardware.
- [x] **3.4 App Breakdown's "cut X by 20 %" nudge** ✅ *(2026-09-15)* The copy
      and the slot already existed; `targetRank` was hardcoded `null`.
      `cutNudgeRank` fills it.

      **The two halves are joined in `MainShell`, never in a ViewModel.**
      `AppBreakdownViewModel` has no `GroupRepository` on purpose, so per-app
      figures have no code path to the cloud (DATA_CONTRACT §3.2). Computing
      this inside it would have been one line shorter and would have quietly
      dismantled that guard. Exactly one number crosses: how much a 20 % cut
      would save. No app name, no app total.

      Reuses `standingOf` rather than counting ranks a third time — the widget
      already showed what happens when the third consumer copies the call and
      drops the guard. Refuses when there is no honest rank, when the cut would
      not move you, when there is no app data, and when you are already first.
      Eight tests, including a group where two members share a total (removing
      *every* matching figure would delete the other person from the comparison
      and promise a rank against one fewer competitor than exists).
- [x] **3.5 Group edge cases** ✅ *(2026-09-15 — already handled, now pinned)*
      All three were done: a group of one renders "You're the only one here"
      instead of a ranking, a group where nobody has synced says so (distinctly
      from "you have no group" — reporting one as the other sends someone to
      join a group they are already in), and non-syncing members are counted in
      "2 of 5 synced today" without being invented as rows.

      `GroupEdgeCaseTest` pins all three. They are branches that only appear for
      a user in an unusual state, which is exactly the branch a refactor breaks
      and a stranger finds.
- [x] **3.6 Orphaned groups** 🟡 *(2026-09-15 — client done, rule pending A)*
      `leaveGroup` now deletes the group when the last member walks out.

      Membership is read **before** the `arrayRemove`, not after: afterwards the
      caller is no longer in `members`, the delete rule could never pass again,
      and the group would be stranded permanently — the exact state this item
      exists to prevent.

      **The rule is written but NOT deployed** — `allow delete` on
      `groups/{groupId}`, gated on the caller being in `members` *and*
      `members.size() == 1`, so nobody can delete a group their friends are
      still in. §2 requires A on rules changes: **REVIEW_LOG #11, pending
      Monish.** The client ships safely without it — the delete is denied, and
      that failure is logged rather than surfaced, because a housekeeping error
      must not report a successful "leave group" as a failure.

      - [x] **3.6a** ✅ *(2026-09-15)* Done, and the count was wrong. **Five**
            group documents existed, not four, and the three candidates were
            three different problems:

            - `J5X6FU` "School Friends" — `members: []`, created by Rohit,
              record holder `[deleted]`. The textbook orphan: everyone left,
              unreachable by any client because every rule needs membership.
              **Deleted, recursively.**
            - `1YO220` — **no document at all.** It appears in the Console list
              only as the parent of a subcollection, which is a different kind
              of orphan and one no membership rule could ever have addressed.
              **Deleted, recursively.**
            - `6NPBWY` "RelTest0914DeleteMe" — **not orphaned.** One member,
              the test account `7Kww…`. Deleted on Rohit's instruction rather
              than as part of the orphan sweep, since it had a member. The test
              account's dangling membership under
              `users/7Kww…/groups/6NPBWY` went with it — deleting a group does
              not cascade to the membership documents that point at it, and
              that pointer would have been an orphan of the opposite kind. The
              Firebase Auth user `7Kww…` itself still exists.

            Verified rather than assumed: a temporary read-only probe in the app
            reported each document's `members` array on device before anything
            was deleted, then again afterwards. Guessing which of three ids was
            disposable would have destroyed a live group, its record and every
            daily total in it, with no undo. The probe was removed immediately
            after.
- [x] **3.7 Accessibility pass** 🟡 *(2026-09-15 — audited and tested; TalkBack
      itself still unrun)* Most of this was already done and the audit mainly
      confirmed it.

      **200 % font scale:** `LargeFontTest` renders Insights, Settings, Privacy,
      App Breakdown and the board at the Android maximum. It is a Compose test
      because **MIUI blocks `settings put system font_scale`**, exactly as it
      blocks `settings put secure` and input injection, so the scale cannot be
      driven on the only phone this project has. The suite includes a control
      that asserts the density override actually applied — without it, every
      other test would pass trivially at 100 % and report surviving a font scale
      it never saw, which is worse than no test.

      **TalkBack labels:** already thorough. Every "Navigate up" is labelled,
      icon-only controls in the group switcher carry real descriptions
      ("Share College Friends invite code"), the week chart describes itself,
      and `formatDistanceSpoken` is wired into four places so a screen reader
      says "less than one metre" rather than "less than one metres". Decorative
      icons beside a text label are deliberately `contentDescription = null` —
      labelling them would make TalkBack read "chevron chevron chevron".

      **Contrast:** `textFaint` (#4A4340, documented "never text") is used only
      for borders and bar fills. Checked every call site; no violations.

      **48 dp targets:** the small `.size()` values are icons *inside* larger
      clickable rows, so the target is the row.

      - [ ] **3.7a** **TalkBack has never actually been run.** The labels are
            present and correct in the source; nobody has swiped through a
            screen with the screen reader on. Enabling it needs
            `settings put secure enabled_accessibility_services`, which MIUI
            blocks — so this is a manual pass on a phone, by hand, and it is the
            only part of 3.7 that a person still has to do.
- [~] **3.8 Phone number linking** — **moved to Stage 4 by Rohit, 2026-09-15.**
- [x] **3.9 Primary group** ✅ *(2026-09-15 — closed by 3.1)* Not cut; finished.
      `setPrimaryGroup()` had been writing `isPrimary` since 2026-09-04 with
      nothing reading it, which is what made "build it or cut it" the right
      question. The widget became the reader it was written for, and there are
      now four: `WidgetSnapshot`, `ProfileViewModel`, `HallOfFameViewModel`, and
      `LeaderboardViewModel`'s active-group fallback. The group switcher writes
      it under "set as widget group".

---

## THE INVITE FLOW — how a stranger becomes a daily user

*(new 2026-09-15, outside the original stages)*

The problem neither Stage 2 nor Stage 3 addressed: a new tester installed,
signed in, and landed on a board reading **"1 of 1 synced today"**. A reverse
leaderboard with one person on it is not a product — there is no game and no
reason to open it tomorrow. Forty installs into forty empty boards is the
likeliest way this fails that has nothing to do with the code working.

- [x] **The landing page** — https://claude.ai/artifact/TzvybJHHWDWYDN3JPJx6C7
      Sent instead of the raw Firebase link. Carries the group code as `?g=CODE`,
      so one page serves every invite. Built to the design system rather than to
      taste: surface and text ladders, the two typefaces, coral spent only on the
      revealed figure and the primary button, hairlines instead of shadows, the
      15 % ambient glow from the sign-in recipe.

      It opens with the app's own question — "How far do you think you scroll
      each day?" — because the guess is the most characteristic thing Scrolla
      has and it was locked behind an install.

      **It does not repeat the 2,800× claim.** Stage 4 records that figure as
      3–7× too high, and a landing page is the last place to put a number the
      project's own notes call wrong. The reveal says the *unit* is wrong, which
      is true and needs no invented figure.

      It also names the three awkward steps before they happen — Google sign-in,
      the App Tester install, the restricted setting. People abandon because a
      flow looks sketchy, not because it is hard, and every unexplained step is
      somewhere to abandon.

- [x] **The deep link** — `scrolla://join/CODE` opens the join screen with the
      code already in it. Verified on device: the link opened Scrolla straight
      onto the six filled cells with nothing typed.

      A custom scheme, not an https App Link: App Links need a domain serving
      `assetlinks.json` and there is none. **The cost is real and is why the page
      exists** — `scrolla://` does nothing at all on a phone without Scrolla, so
      this can never be the link you send to someone you are inviting. The page
      branches; the scheme is the "already have it" path.

      `onNewIntent` is handled, not just `onCreate`. Without it a link tapped
      while Scrolla was already open would have done nothing — the quietest
      possible failure for the one feature whose job is getting people in.

- [x] **A banner was covering every detail screen's header.** Found by
      screenshotting the deep link. `TrackingOffBanner` and `TrackingGapBanner`
      are aligned to the top of the outer `Box`, which makes them an overlay
      rather than a row, so they sat on top of whatever screen was pushed —
      hiding its back arrow and its title.

      The join screen is the worst place for that and the likeliest to hit it: a
      new user has tracking off by definition, so the first screen an invite
      opened for them had no visible way back and no title. Both banners are now
      scoped to `MainTabs`.

- [ ] **Send the page, not the Firebase link.** The invite link still goes out
      raw. `https://claude.ai/artifact/TzvybJHHWDWYDN3JPJx6C7?g=GUW4YE`

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
| **Phone number linking** *(S1.B4, was 3.8)* | Cut 2026-09-15. Costs reCAPTCHA/Play Integrity setup, Spark's SMS limits and a second auth path to test, and buys nothing over Google sign-in, which is verified end to end. Settings already says "Not yet available", which is true rather than a placeholder. |
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
  unreviewed**.

  **Now checked rather than assumed: the repo copy and the live copy are
  byte-identical.** Pulled the deployed ruleset from the Firebase Rules API on
  2026-09-14 and diffed it against `firestore/firestore.rules` — 7,565 bytes
  each, no differences (the 8-byte discrepancy on disk is only CRLF). The
  timeline matches: the rules were committed at 11:49:19 IST on 2026-09-06 and
  the live ruleset `34148ade-5012-4b94-b837-50e6ff75e474` was created at
  11:51:28 IST, two minutes later, and nothing has touched the file since.

  **So there is nothing to deploy, and that is the good outcome.** It also
  means reviewing `firestore/firestore.rules` in git *is* reviewing production,
  exactly — which is what #10 needs and could not previously be said with
  confidence.

  Until 2026-09-14 there was no `firebase.json` or `.firebaserc`, so the rules
  had only ever reached the console by hand — which is why this project once
  believed the rules were undeployed for eleven days while they were live. Both
  files now exist and the repo copy compiles under `--dry-run`, so the *next*
  change can be deployed from the repo and reviewed as a diff instead of
  pasted. #10 still needs Monish's eyes on the content. Reviews #2 and #4 each caught a real bug; #4 found that group
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
