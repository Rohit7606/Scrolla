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

### P0.1 — Tests exist at all `[Both]`

There is currently **no `app/src/test/` and no `app/src/androidTest/` directory**.
Not a thin suite — zero files. Meanwhile `app/build.gradle.kts` already declares
`testInstrumentationRunner`, JUnit, Espresso, and the Compose test artifacts.
Every hook is wired and nothing hangs on them.

This is ranked first because it matches our actual bug history. The last four
sessions were: metres rendered with a km label, a chart anchored to the wrong
day, a null health row mapping to ACTIVE, a landmark shown for 10 cm of
scrolling. All four are pure-function logic bugs — the exact class of bug a unit
test catches instantly and a device test catches slowly or never.

- [ ] **P0.1a** Create `app/src/test/kotlin/com/scrolla/`.
- [ ] **P0.1b** `DistanceFormatterTest` — 88 lines of pure functions, the cheapest
      high-value test in the repo. Cover: the metre/km crossover at 999.5 m in
      both directions, `formatDistance` unit agreement (the delta-chip bug),
      `nearestLandmark` returning the nearest entry however absurd (this is
      contract, and the ratio gate that hides it lives in `HomeViewModel` — test
      both halves so nobody "fixes" the contract later).
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

### P0.3 — Nothing reports crashes `[Both]`

Firebase is already in the project. Crashlytics is not. When the app dies on
someone else's phone you find out because they mention it, or you don't.

- [ ] **P0.3a** Add Firebase Crashlytics.
- [ ] **P0.3b** Non-fatal reports on the paths that currently swallow errors — the
      `catch` blocks in `ScrollRepository` that log and return a safe default are
      correct for the UI and invisible to us.
- [ ] **P0.3c** Confirm no scroll content, package list, or user identity beyond
      the Firebase UID reaches a crash report. See P1.2.

### P0.4 — Delete account is a dead row `[B]`

`SettingsScreen.kt:300-304` renders a "Delete" row with `enabled = false`. It has
never done anything.

For an app that holds a Google identity and writes to Firestore this is a Play
requirement. For an app built on an AccessibilityService it is *the* trust
affordance — you cannot ask someone to let you observe every scroll they make
and then offer no exit.

- [ ] **P0.4a** Implement deletion: Firebase Auth account, `/users/{uid}`, the
      user's `dailyTotals` documents in every group they belong to, and their
      entry in each group's `members` array.
- [ ] **P0.4b** Firestore rules for self-deletion — `isSelfLeave()` does not exist
      yet, and the current rules only permit `isSelfJoin()` and
      `isRecordImprovement()`. Needs A's review per §2.
- [ ] **P0.4c** Wipe the local Room database on delete. Otherwise "delete my
      account" leaves every scroll event on the device.
- [ ] **P0.4d** Confirmation dialogue with real consequences spelled out, not a
      generic "Are you sure?".
- [ ] **P0.4e** While you are there: local data export. Cheap once deletion has
      already enumerated everything, and it is the other half of the same promise.

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
- [ ] **P1.1b** `.gitignore` contains a block of mangled UTF-16 lines around the
      `scrolla_database` entries — a space between every character, so those
      patterns match nothing. Rewrite the file as UTF-8.

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

### P2.1 — The second-account test `[Both]`

**Still the single highest-value outstanding item in the project.** No group has
ever had more than one participant. S2.4, S2.5, S2.9, S3.4 and the S3.2 nudge are
all blocked behind it, and the last round proved that code written without a real
second party tends to be wrong in ways review does not catch.

- [ ] **P2.1a** Second Google account, second physical device.
- [ ] **P2.1b** Create a group on device 1, share the code, join from device 2.
- [ ] **P2.1c** Both devices scroll; confirm both appear on both leaderboards with
      the right numbers and the right ascending order.
- [ ] **P2.1d** Confirm the group record (`recordKm`) updates and that the
      improvement-only rule behaves with two writers.
- [ ] **P2.1e** Log it in `DEVICE_TEST_LOG.md`.

### P2.2 — Screens never run on a device `[B]`

Personal Records, Hall of Fame, App Breakdown, Weekly Recap and Settings are
compile-verified and reasoned about only. Home, Insights and the group flows have
had a real device pass.

- [ ] **P2.2a** Device pass on each of the five, with real data present.
- [ ] **P2.2b** And each with *no* data present — the empty state is where the
      honesty rule usually breaks.

### P2.3 — Errors reach the user `[B]` — *this is S2.8, still open*

**There is no `Snackbar` anywhere in the app.** Zero occurrences. Only three
screens render `errorMessage` at all (Create Group, Join Group, Leaderboard).
`LeaderboardViewModel.setPrimaryGroup` sets an error message that has no path to
a human being.

- [ ] **P2.3a** A `SnackbarHost` in `MainShell` and a shared way for any ViewModel
      to push a transient message to it.
- [ ] **P2.3b** Route every existing `errorMessage` into it.
- [ ] **P2.3c** Errors that are recoverable get a retry action, not just a
      complaint.
- [ ] **P2.3d** Distinguish "offline" from "failed" — nothing in the app currently
      checks connectivity at all (no `ConnectivityManager` usage anywhere), so a
      Firestore failure while offline reads as a generic error.

### P2.4 — `isServiceRunning` is inferred, not observed `[A]`

`onServiceConnected()` does not set `isServiceRunning = true` — only a successful
`flushBatch()` does. So a service enabled ten seconds ago has the flag `false`
through no fault of its own.

The Settings health card currently guards around this by mapping a never-flushed
row to UNKNOWN. That guard is a UI-side patch over a service-side inaccuracy.

- [ ] **P2.4a** Set the flag when the service actually connects. The service knows
      the moment it happens; inferring it from the first flush is strictly worse
      information. A's call, A's file.
- [ ] **P2.4b** Once done, revisit whether the UNKNOWN guard in `SettingsScreen`
      is still needed or is now hiding real INTERRUPTED states.

### P2.5 — Push what exists `[B]`

- [ ] **P2.5a** `b/group-flow-fixes` has **7 unpushed commits**, including the
      chart-rolling and accessibility-on-resume fixes. Push, PR, merge.

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

- [ ] **P3.2a** Haptics on primary buttons, tab switches, and — most of all — a
      personal record being broken. That last one is the app's emotional payload
      and it currently lands silently.
- [ ] **P3.2b** No pull-to-refresh anywhere. The Leaderboard deliberately caches
      behind a staleness window to protect the Firestore quota — correct — and
      gives the user no way to say "no, check now". Add `PullToRefreshBox`,
      bypassing the staleness check on an explicit gesture.
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

### P4.2 — Fake numbers still in the source `[B]`

- [ ] **P4.2a** `HomeScreen.kt:64` and `LeaderboardScreen.kt:61` carry `2.8f` as
      **default parameter values**. Preview scaffolding today, and one careless
      call site away from being rendered to a user. Remove the defaults or make
      them obviously absurd.

### P4.3 — Dead weight `[Both]`

- [ ] **P4.3a** `libs.firebase.ai` is declared in `build.gradle.kts` with **zero
      usages** in the source. Remove it.
- [ ] **P4.3b** Re-run the written-but-never-rendered string audit after P3.1.
      It found 58 last time and pointed straight at several real bugs; it is the
      highest-yield cheap audit we have.

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
