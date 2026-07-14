# SOCIAL_PROGRESS.md — Person B (Social & Experience Track)
**Owner:** Person B
**Track:** `ui/`, `firestore/`, `auth/`, `leaderboard/`, `gamification/`
**Last updated:** 2026-07-14
**AI agents reading this:** This is Person B's working file. Before suggesting any implementation in B's folders, read the current status, known issues, and dependency sections. Never suggest wiring a Compose screen to real sensor data until Section 1's handoff status shows ✅. Cross-reference `DATA_CONTRACT.md` Section 4 for every function B calls from `ScrollRepository`. Never write to Firestore daily totals directly — only `triggerFirestoreSync()` does that (A's function, B calls it on a timer).

---

## 1. CURRENT STATUS — ONE-LINER FOR PERSON A

> _(B updates this line at the end of every session so A knows where things stand without reading the whole file)_

**Sprint 1 (B's sub-track):** Not started
**Sprint 2:** Not started — waiting on Sprint 0 sensor verification from A
**Sprint 3:** Not started

**Waiting on A for:**
- `ScrollRepository` implementation (needed for Sprint 2 UI) — status per `SENSOR_PROGRESS.md` Section 7
- Sprint 0 sensor accuracy verification — status per `SENSOR_PROGRESS.md` Section 1

**What B is building against right now:** Stub data only. Every ViewModel that calls a `ScrollRepository` function must have a clearly commented stub:
```kotlin
// STUB — replace with scrollRepository.getTodayTotalKm() after A confirms S1.A9 complete
val todayKm = 0.0f
```

---

## 2. SCREEN STATUS — ALL 15 SCREENS

Each screen is tracked independently. A screen is not "done" until it has: real data wired (not stubs), an empty state, an error state, and has been manually checked in the simulator plus at least one physical device.

### Layer 1 — Onboarding (Screens 1–4)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 1 | Sign in with Google | `ui/auth/SignInScreen.kt` | 🔴 Not started | N/A | N/A | ☐ auth failure handled | ☐ |
| 2 | Welcome | `ui/onboarding/WelcomeScreen.kt` | 🔴 Not started | N/A | N/A | N/A | ☐ |
| 3 | Permission ask | `ui/onboarding/PermissionScreen.kt` | 🔴 Not started | N/A | ☐ | ☐ denied state | ☐ |
| 4 | Join or create group | `ui/onboarding/GroupSetupScreen.kt` | 🔴 Not started | ☐ | ☐ | ☐ invalid code | ☐ |

### Layer 2 — Main Tabs (Screens 5–7)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 5 | Home (default tab) | `ui/home/HomeScreen.kt` | 🔴 Not started | ☐ stub only | ☐ | ☐ sync error | ☐ |
| 6 | Leaderboard | `ui/leaderboard/LeaderboardScreen.kt` | 🔴 Not started | ☐ stub only | ☐ group of 1 | ☐ network error | ☐ |
| 7 | Insights | `ui/insights/InsightsScreen.kt` | 🔴 Not started | ☐ stub only | ☐ no data yet | ☐ | ☐ |

### Layer 3 — Detail Screens (Screens 8–15)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 8 | Service Health | `ui/settings/ServiceHealthScreen.kt` | 🔴 Not started | ☐ Flow from A | N/A | ☐ service dead | ☐ |
| 9 | Group switcher list | `ui/groups/GroupSwitcherScreen.kt` | 🔴 Not started | ☐ | ☐ no groups yet | ☐ | ☐ |
| 10 | Join group | `ui/groups/JoinGroupScreen.kt` | 🔴 Not started | ☐ | N/A | ☐ code not found | ☐ |
| 11 | Weekly recap card | `ui/gamification/RecapCardScreen.kt` | 🔴 Not started | ☐ | ☐ not enough data | ☐ | ☐ |
| 12 | Personal records | `ui/gamification/PersonalRecordsScreen.kt` | 🔴 Not started | ☐ | ☐ fewer than 1 day | ☐ | ☐ |
| 13 | Group hall of fame | `ui/gamification/HallOfFameScreen.kt` | 🔴 Not started | ☐ | ☐ record not set yet | ☐ | ☐ |
| 14 | App breakdown detail | `ui/insights/AppBreakdownScreen.kt` | 🔴 Not started | ☐ | ☐ no app data | ☐ | ☐ |
| 15 | Profile | `ui/profile/ProfileScreen.kt` | 🔴 Not started | ☐ | N/A | ☐ delete failed | ☐ |

**Status key:** 🔴 Not started · 🟡 In progress · 🟢 Code complete · ✅ Verified on device

---

## 3. COMPONENT STATUS — NON-SCREEN WORK

| Component | File(s) | Status | Verified? | Notes |
|---|---|---|---|---|
| Firebase project config | `google-services.json` + `build.gradle` | 🟡 In progress | ☐ | Firebase Auth + Firestore configured, Google Sign-In implemented |
| Firebase Auth — Google sign-in | `auth/AuthRepository.kt` | 🟡 In progress | ☐ | Google Sign-In flow implemented with Firebase credential exchange |
| Firebase Auth — phone linking | `auth/AuthRepository.kt` | 🔴 Not started | ☐ | Test: same UID before and after linking |
| Firestore security rules | `firestore/firestore.rules` | 🔴 Not started | ☐ | A must review before deploy — log in REVIEW_LOG.md |
| Group create flow | `firestore/GroupRepository.kt` | 🔴 Not started | ☐ | |
| Group join flow | `firestore/GroupRepository.kt` | 🔴 Not started | ☐ | |
| Group membership list | `firestore/GroupRepository.kt` | 🔴 Not started | ☐ | |
| Firestore sync timer | `leaderboard/LeaderboardViewModel.kt` | 🔴 Not started | ☐ | 15-min interval, also on foreground |
| Leaderboard polling | `leaderboard/LeaderboardRepository.kt` | 🔴 Not started | ☐ | No onSnapshot() |
| Multi-group write | `firestore/SyncManager.kt` | 🔴 Not started | ☐ | Writes to all groups user is in |
| Hall of fame update | `firestore/GroupRepository.kt` | 🔴 Not started | ☐ | Updates group record on each sync |
| Most improved calculation | `leaderboard/LeaderboardRepository.kt` | 🔴 Not started | ☐ | Week-over-week delta |
| Most consistent calculation | `leaderboard/LeaderboardRepository.kt` | 🔴 Not started | ☐ | Lowest variance over 7 days |
| Recap card image generation | `gamification/RecapCardGenerator.kt` | 🔴 Not started | ☐ | Android Bitmap + share intent |
| Account deletion flow | `auth/AuthRepository.kt` | 🔴 Not started | ☐ | Deletes Auth + Firestore docs |
| DistanceFormatter usage | across all screens | 🔴 Not started | ☐ | No inline km formatting anywhere |

---

## 4. FIRESTORE SYNC — VERIFICATION LOG

Every time B verifies that the Firestore sync is working end-to-end, log it here. This is separate from `DEVICE_TEST_LOG.md` (which is A's device/OEM testing) — this is specifically about data appearing correctly in the cloud.

| # | Date | Test | Expected | Actual | Pass? | Notes |
|---|---|---|---|---|---|---|
| — | — | No sync tests run yet | — | — | — | — |

**Critical sync tests to run before Sprint 2 is marked complete:**

**S2.T1 — Basic sync:** Scroll for 15 minutes on a physical device. Open Firebase console → Firestore → `/groups/{groupId}/dailyTotals/`. Confirm a document exists with the correct `userId`, today's date string, and a non-zero `totalKm`.

**S2.T2 — 15-minute timer fires:** Background the app after completing S2.T1. Wait 16 minutes. Check Firebase console again — confirm `updatedAt` timestamp has advanced and `totalKm` has increased if more scrolling happened.

**S2.T3 — Multi-group write:** Add a second group. Confirm the same `userId_date` document appears under *both* groups' `dailyTotals` collection after the next sync.

**S2.T4 — No `onSnapshot()` anywhere:** Search the entire `firestore/` and `leaderboard/` directories for `addSnapshotListener`. Result must be zero matches. This is a hard rule from `AGENTS.md` Section 4.5.

**S2.T5 — Staleness check works:** Open the leaderboard tab. Note the Firestore read count in the Firebase console. Switch to another tab and back within 2 minutes. Confirm no additional read was made (staleness check from `ScrollaConstants.LEADERBOARD_CACHE_STALE_MS` prevented it).

---

## 5. EMPTY STATE & ERROR STATE TRACKER

Per the loophole audit and `SPRINT_LOG.md` S2.8 and S3.x: empty states and error states are built deliberately, not deferred. Log each one explicitly.

| Screen | Empty state | Empty state wired? | Error state | Error state wired? |
|---|---|---|---|---|
| Home | "No data yet — keep scrolling today" | ☐ | Sync error banner with last-synced time | ☐ |
| Leaderboard | Personal stats when group size = 1 | ☐ | "Couldn't load — tap to retry" | ☐ |
| Leaderboard | "[left]" label for departed members | ☐ | — | — |
| Insights | "Start scrolling to see your stats" | ☐ | — | — |
| Group switcher | "You're not in any groups yet" | ☐ | — | — |
| Join group | — | — | "Group not found — check the code" | ☐ |
| Personal records | "Need at least 2 days of data" | ☐ | — | — |
| Hall of fame | "No record set yet — you could be first" | ☐ | — | — |
| Recap card | "Need a full week of data" | ☐ | — | — |
| App breakdown | "No app data yet today" | ☐ | — | — |
| Service Health | — | — | "Tracking stopped — tap to fix" | ☐ |
| Sign in | — | — | "Sign-in failed — try again" | ☐ |
| Profile — delete account | — | — | "Couldn't delete — try again" | ☐ |

> **Rule:** A blank white screen or a crash is never an acceptable empty or error state. If you're unsure what the empty state should say, check `UI_COPY.md` before inventing copy inline.

---

## 6. GAMIFICATION FEATURE STATUS

| Feature | Screen | Status | Data source | Notes |
|---|---|---|---|---|
| Reverse leaderboard (lowest wins) | Screen 6 | 🔴 Not started | Firestore dailyTotals | Ranked ascending by totalKm |
| Landmark comparison | Screen 5 | 🔴 Not started | `DistanceFormatter.nearestLandmark()` | |
| Most improved highlight | Screen 6 | 🔴 Not started | Firestore — week-over-week delta | Week = Mon–Sun, not rolling 7 days |
| Most consistent recognition | Screen 6 | 🔴 Not started | Firestore — 7-day variance | Lowest variance wins |
| Personal records | Screen 12 | 🔴 Not started | `getPersonalBestDay()` from Room | |
| Time-of-day insight framing | Screen 5 rotating | 🔴 Not started | `getTodayPeakHour()` from Room | Template: "most of your scrolling happens [hour]–[hour+1] — that's your commute distance, but at midnight" |
| App-comparison nudge | Screen 14 | 🔴 Not started | `getTodayTopApps()` from Room | "cutting [app] by 20% would put you in 1st" — never synced |
| Group hall of fame | Screen 13 | 🔴 Not started | Firestore group metadata | Show "progress toward record" alongside absolute best |
| Weekly recap shareable card | Screen 11 | 🔴 Not started | Room + Firestore | Android Bitmap + share intent |
| Home rotating insight | Screen 5 | 🔴 Not started | Rotates: personal record / peak hour / app nudge | One card only, not all three simultaneously |

**Rotation logic for the Home screen insight card:**
Priority order (show the first one that has data available):
1. A new personal record was set today → show "new personal best"
2. No record today → show peak-hour insight if `getTodayPeakHour()` returns non-null
3. No peak hour yet → show app-comparison nudge if `getTodayTopApps()` returns non-empty
4. No app data yet → show a placeholder ("scroll today to see your insights")

---

## 7. LEADERBOARD CORRECTNESS CHECKLIST

The reverse leaderboard is the core mechanic of the whole app. Check every item here before considering the leaderboard screen complete:

- [ ] Documents sorted **ascending** by `totalKm` — lowest km = Rank 1 (winner). A descending sort is the first mistake an AI agent will make since most leaderboards sort descending.
- [ ] "You" row is visually distinct — highlighted accent colour, not just rank number.
- [ ] Rank 1 row uses a success/green highlight — the winner has the *smallest* number, which looks counterintuitive and needs visual reinforcement.
- [ ] Numbers show `totalKm` only — no per-app breakdown, no `topApp` field, no insight about *why* a user's number is high. Per `DATA_CONTRACT.md` Section 3.2 and `scrolla_project_summary.md` Section 5 (privacy fix).
- [ ] A group of exactly 1 member shows personal stats, not "Rank 1 of 1" (that would make the reverse mechanic look broken).
- [ ] A departed member shows "[left]" next to their name in historical views, not their actual display name.
- [ ] Group switcher correctly re-fetches the leaderboard for the newly selected group, not cached data from the previous group.
- [ ] Most improved banner is visually separate from the ranked list — it's a different kind of recognition, not "rank 0."
- [ ] Leaderboard data refreshes on tab open (with staleness check) AND when `triggerFirestoreSync()` completes — not on a separate timer.

---

## 8. AUTH CORRECTNESS CHECKLIST

- [ ] Firebase Auth UID is the `userId` everywhere — confirmed in Logcat on first sign-in that it matches what's in the Firestore `/users/` collection.
- [ ] Reinstall test: sign in → use app → uninstall → reinstall → sign in with same Google account → confirm same `userId` → confirm group membership is restored from Firestore → confirm leaderboard shows the same historical data.
- [ ] Phone linking test: sign in with Google → link phone number in Profile → sign out → sign in with phone number → confirm same `userId` returned → confirm group membership intact.
- [ ] Account deletion test: create a test account → join a group → scroll for 1 day → delete account → confirm: (a) Firebase Auth account gone, (b) `/users/{userId}/` documents deleted, (c) name in any hall-of-fame entry replaced with "[deleted]", (d) `dailyTotals` documents in group deleted, (e) app returns to Sign In screen.

---

## 9. IMPLEMENTATION DECISIONS LOG

Same purpose as A's decisions log — prevents an AI agent from "correcting" an intentional choice. B logs here.

| # | Date | Decision | Reason | Affects A? |
|---|---|---|---|---|
| — | — | No decisions logged yet | — | — |

---

## 10. KNOWN ISSUES & ACTIVE BUGS

| # | Discovered | Description | Severity | Sprint | Resolved? |
|---|---|---|---|---|---|
| — | — | No known issues yet | — | — | — |

**Severity guide:**
- 🔴 **Critical:** Wrong data shown to user (wrong km, wrong rank, phantom Firestore reads). Blocks release.
- 🟡 **High:** Reproducible wrong state on a specific flow (e.g. multi-group sync only writes to primary group). Fix before Sprint 3 ends.
- 🟢 **Low:** Cosmetic, edge case, or non-reproducible. Can defer.

---

## 11. WHAT B MUST NEVER DO (repeated here for agent context)

Full list in `AGENTS.md` Section 4 and `DATA_CONTRACT.md` Section 7. The most commonly violated ones by an AI agent:

- **Never add `addSnapshotListener()` anywhere.** Not even temporarily for debugging. Poll instead.
- **Never write `totalKm` to Firestore directly.** Call `triggerFirestoreSync()` from A's `ScrollRepository` — A handles the write.
- **Never add per-app data to a Firestore document.** `getTodayTopApps()` is Room-only and display-only.
- **Never sort the leaderboard descending.** Lowest `totalKm` = Rank 1. Always ascending.
- **Never call `LocalDate.now(ZoneOffset.UTC)`.** Local time only.
- **Never inline km formatting.** Always use `DistanceFormatter.formatKm()`.
- **Never deploy Firestore rules without A's review.** Log in `REVIEW_LOG.md` first.
- **Never leave a stub value in a screen that gets merged into main.** Stubs are fine on B's branch; they must be replaced with real `ScrollRepository` calls before a PR is opened.

---

## 12. WHAT A MUST NEVER CHANGE IN B'S FILES (for completeness)

- `firestore/firestore.rules` — any change requires a new M2 review (A re-reviews their own approval)
- Any ViewModel that calls `ScrollRepository` functions — if a function signature changes, A updates `DATA_CONTRACT.md` and tells B before changing the implementation
- `ui/settings/ServiceHealthScreen.kt` — this screen depends on `observeServiceHealth()` Flow; changes to the Flow's emitted type require B's knowledge

---

## 13. SESSION NOTES

A running scratchpad for in-progress thoughts, things to pick up next session, questions for A. Not structured — just useful.

> _(Add dated notes here as you work. Archive completed notes to a collapsible section once resolved.)_

**2026-07-14**
- Configured Firebase project with Auth and Firestore dependencies
- Updated gradle/libs.versions.toml with Firebase BOM and library versions
- Updated app/build.gradle.kts to implement Firebase Auth and Firestore
- Verified and updated google-services.json to include auth and festore services
- Created ScrollaApplication.kt to initialize Firebase (though auto-initialized via plugin)
- Set android:name=".auth.ScrollaApplication" in AndroidManifest.xml
- Created AuthRepository.kt with Google Sign-In credential handling
- Created SignInActivity.kt with complete Google Sign-In flow
- Updated AndroidManifest.xml to set SignInActivity as launcher
- Added placeholder string for default_web_client_id in strings.xml
- Committed changes to branch b/firebase-auth-setup
- Updated SOCIAL_PROGRESS.md to reflect in-progress status

---

*This file is owned by Person B. A reads Section 1 to know what B is waiting on. AI agents read it to avoid re-litigating implementation decisions already made.*
