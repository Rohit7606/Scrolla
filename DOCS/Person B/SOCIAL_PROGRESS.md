# SOCIAL_PROGRESS.md — Person B (Social & Experience Track)
**Owner:** Person B
**Track:** `ui/`, `firestore/`, `auth/`, `leaderboard/`, `gamification/`
**Last updated:** 2026-08-16
**AI agents reading this:** This is Person B's working file. Before suggesting any implementation in B's folders, read the current status, known issues, and dependency sections. Never suggest wiring a Compose screen to real sensor data until Section 1's handoff status shows ✅. Cross-reference `DATA_CONTRACT.md` Section 4 for every function B calls from `ScrollRepository`. Never write to Firestore daily totals directly — only `triggerFirestoreSync()` does that (A's function, B calls it on a timer).

---

## 1. CURRENT STATUS — ONE-LINER FOR PERSON A

> _(B updates this line at the end of every session so A knows where things stand without reading the whole file)_

**Sprint 1 (B's sub-track):** 🟡 In progress — S1.B1–B3 complete, S1.B4–B10 remaining
**Sprint 2:** 🟡 Partially unblocked — All 15 Compose screens exist as UI shells (mock data), navigation wired via MainShell. Waiting on real `ScrollRepository` wiring + Firestore backend.
**Sprint 3:** Not started

**Waiting on A for:**
- `DistanceFormatter.cmToKm()` — still missing from `model/DistanceFormatter.kt` per S1.A9 known gap. B's screens use `ScrollaFormatters` (presentation-only) as a stopgap, but real data flow needs the model-layer function.
- Confirmation that `triggerFirestoreSync()` stub in `ScrollRepository` is ready to be replaced with real Firestore write logic (B owns the Firestore side, but A owns the function signature).

**What B has built so far:** All 15 Compose screens exist as UI shells with mock/default data. Full navigation architecture (`MainShell` + `ScreenRoute` sealed class) is wired in `MainActivity`. Splash → SignIn → Onboarding → Home flow is complete with SharedPreferences persistence for `isFirstLaunch`. Firebase Auth (Google Sign-In) is functional end-to-end. No Firestore backend code exists yet (no rules, no repositories, no sync logic).

---

## 2. SCREEN STATUS — ALL 15 SCREENS

Each screen is tracked independently. A screen is not "done" until it has: real data wired (not stubs), an empty state, an error state, and has been manually checked in the simulator plus at least one physical device.

### Layer 1 — Onboarding (Screens 1–4)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 1 | Splash | `ui/screens/SplashScreen.kt` | 🟢 Code complete | N/A | N/A | N/A | ☑ |
| 1 | Sign in with Google | `ui/screens/SignInScreen.kt` | 🟢 Code complete | N/A | N/A | ☑ auth failure handled | ☑ |
| 2-4 | Onboarding (Welcome, Permission, Battery, Join Group) | `ui/screens/OnboardingScreen.kt` | 🟢 Code complete | N/A | N/A | N/A | ☑ |

### Layer 2 — Main Tabs (Screens 5–8)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 5 | Home (default tab) | `ui/screens/HomeScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ | ☐ sync error | ☐ |
| 6 | Leaderboard | `ui/screens/LeaderboardScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ group of 1 | ☐ network error | ☐ |
| 7 | Insights | `ui/screens/InsightsScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ no data yet | ☐ | ☐ |
| 8 | Profile | `ui/screens/ProfileScreen.kt` | 🟡 UI shell complete | ☐ mock data | N/A | ☐ delete failed | ☐ |

### Layer 3 — Detail Screens (Screens 9–15)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 9 | Settings + Service Health | `ui/screens/SettingsScreen.kt` | 🟡 UI shell complete | ☐ mock `ServiceHealthState` enum | N/A | ☐ service dead | ☐ |
| 10 | Group switcher list | `ui/screens/GroupSwitcherScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ no groups yet | ☐ | ☐ |
| 11 | Join group | `ui/screens/JoinGroupScreen.kt` | 🟡 UI shell complete | ☐ | N/A | ☐ code not found | ☐ |
| — | Create group | `ui/screens/CreateGroupScreen.kt` | 🟡 UI shell complete | ☐ | N/A | ☐ | ☐ |
| 12 | Weekly recap card | `ui/screens/WeeklyRecapScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ not enough data | ☐ | ☐ |
| 13 | Personal records | `ui/screens/PersonalRecordsScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ fewer than 1 day | ☐ | ☐ |
| 14 | Group hall of fame | `ui/screens/HallOfFameScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ record not set yet | ☐ | ☐ |
| 15 | App breakdown detail | `ui/screens/AppBreakdownScreen.kt` | 🟡 UI shell complete | ☐ mock data | ☐ no app data | ☐ | ☐ |

**Status key:** 🔴 Not started · 🟡 In progress / UI shell · 🟢 Code complete · ✅ Verified on device

> **Note on file paths:** The original plan had screens in subdirectories (`ui/home/`, `ui/leaderboard/`, etc.) but the actual implementation puts all screens flat under `ui/screens/`. This is the current reality. Service Health is integrated into `SettingsScreen.kt` rather than being a separate `ServiceHealthScreen.kt`.

---

## 3. COMPONENT STATUS — NON-SCREEN WORK

| Component | File(s) | Status | Verified? | Notes |
|---|---|---|---|---|
| Firebase project config | `google-services.json` + `build.gradle.kts` | 🟢 Complete | ☑ | Firebase Auth + Firestore deps configured, SHA-1 fingerprint added |
| Firebase Auth — Google sign-in | `auth/AuthRepository.kt` + `ui/auth/SignInActivity.kt` + `ui/screens/SignInScreen.kt` | 🟢 Complete | ☑ | Google Sign-In flow implemented with Firebase credential exchange. Dual path: `SignInActivity` (legacy activity-based) and `SignInScreen` (Compose-based, used in current flow) |
| Firebase Auth — phone linking | `auth/AuthRepository.kt` | 🔴 Not started | ☐ | Test: same UID before and after linking |
| Firestore security rules | `firestore/firestore.rules` | 🟢 Complete | ☑ | Deployed to Firebase Console and verified in Rules Playground. M2 Review logged. |
| Group create flow | `firestore/GroupRepository.kt` | 🟢 Code complete | ☐ | Backend logic implemented. Needs UI wiring. |
| Group join flow | `firestore/GroupRepository.kt` | 🟢 Code complete | ☐ | Backend logic implemented. Needs UI wiring. |
| Group membership list | — | 🔴 Not started | ☐ | UI shell exists (`GroupSwitcherScreen.kt`), no backend |
| Firestore sync timer | — | 🔴 Not started | ☐ | 15-min interval, also on foreground |
| Leaderboard polling | — | 🔴 Not started | ☐ | No onSnapshot() |
| Multi-group write | — | 🔴 Not started | ☐ | Writes to all groups user is in |
| Hall of fame update | — | 🔴 Not started | ☐ | Updates group record on each sync |
| Most improved calculation | — | 🔴 Not started | ☐ | Week-over-week delta |
| Most consistent calculation | — | 🔴 Not started | ☐ | Lowest variance over 7 days |
| Recap card image generation | — | 🔴 Not started | ☐ | Android Bitmap + share intent |
| Account deletion flow | `auth/AuthRepository.kt` | 🔴 Not started | ☐ | Deletes Auth + Firestore docs |
| ScrollaFormatters (presentation) | `ui/screens/ScrollaFormatters.kt` | 🟢 Complete | ☑ | `formatDistance()` and `formatOrdinal()` — presentation-only, used across screens |
| ScrollaStrings (UI copy) | `ui/screens/ScrollaStrings.kt` | 🟢 Complete | ☑ | All 246 lines of centralized UI copy for every screen |
| Design System (modifiers) | `ui/components/DesignSystem.kt` | 🟢 Complete | ☑ | `bounceClick()` and `bentoCard()` modifier extensions |
| ScrollaPrimaryButton | `ui/components/ScrollaPrimaryButton.kt` | 🟢 Complete | ☑ | Shared primary CTA button component |
| Theme system | `ui/theme/*.kt` (7 files) | 🟢 Complete | ☑ | Color, Type, Spacing, Shape, Motion, ExtendedColors, Theme |
| MainShell navigation | `ui/screens/MainShell.kt` | 🟢 Complete | ☑ | `ScreenRoute` sealed class, 4-tab nav bar, stack-based detail navigation |
| SplashScreen + ViewModel | `ui/screens/SplashScreen.kt` + `SplashViewModel.kt` | 🟢 Complete | ☑ | Animated splash with wordmark |
| MainActivity navigation | `MainActivity.kt` | 🟢 Complete | ☑ | Splash → SignIn → Onboarding → Home flow with SharedPreferences `isFirstLaunch` persistence |

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
| Settings / Service Health | — | — | "Tracking stopped — tap to fix" | ☐ |
| Sign in | — | — | "Sign-in failed — try again" | ☑ (onSignInError callback wired) |
| Profile — delete account | — | — | "Couldn't delete — try again" | ☐ |

> **Rule:** A blank white screen or a crash is never an acceptable empty or error state. If you're unsure what the empty state should say, check `UI_COPY.md` before inventing copy inline.

---

## 6. GAMIFICATION FEATURE STATUS

| Feature | Screen | Status | Data source | Notes |
|---|---|---|---|---|
| Reverse leaderboard (lowest wins) | Screen 6 | 🟡 UI shell | Mock data | Ranked ascending by totalKm — UI built, no Firestore wiring |
| Landmark comparison | Screen 5 | 🟡 UI shell | Mock string | Placeholder text, not wired to `DistanceFormatter.nearestLandmark()` |
| Most improved highlight | Screen 6 | 🟡 UI shell | Mock string | Banner UI exists with placeholder, not wired to Firestore |
| Most consistent recognition | Screen 6 | 🔴 Not started | Firestore — 7-day variance | Lowest variance wins |
| Personal records | Screen 13 | 🟡 UI shell | Mock data | `PersonalRecordsScreen.kt` exists with mock records |
| Time-of-day insight framing | Screen 5 rotating | 🟡 UI shell | Mock string | Insight card exists in HomeScreen with hardcoded text |
| App-comparison nudge | Screen 15 | 🔴 Not started | `getTodayTopApps()` from Room | "cutting [app] by 20% would put you in 1st" — never synced |
| Group hall of fame | Screen 14 | 🟡 UI shell | Mock data | `HallOfFameScreen.kt` exists with mock record holder |
| Weekly recap shareable card | Screen 12 | 🟡 UI shell | Mock data | `WeeklyRecapScreen.kt` exists, share button present, no Bitmap generation |
| Home rotating insight | Screen 5 | 🟡 UI shell | Mock string | One card visible, rotation logic not implemented |

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

- [x] Firebase Auth UID is the `userId` everywhere — confirmed via `AuthRepository().currentUser` in `MainActivity.kt`.
- [ ] Reinstall test: sign in → use app → uninstall → reinstall → sign in with same Google account → confirm same `userId` → confirm group membership is restored from Firestore → confirm leaderboard shows the same historical data.
- [ ] Phone linking test: sign in with Google → link phone number in Profile → sign out → sign in with phone number → confirm same `userId` returned → confirm group membership intact.
- [ ] Account deletion test: create a test account → join a group → scroll for 1 day → delete account → confirm: (a) Firebase Auth account gone, (b) `/users/{userId}/` documents deleted, (c) name in any hall-of-fame entry replaced with "[deleted]", (d) `dailyTotals` documents in group deleted, (e) app returns to Sign In screen.

---

## 9. IMPLEMENTATION DECISIONS LOG

Same purpose as A's decisions log — prevents an AI agent from "correcting" an intentional choice. B logs here.

| # | Date | Decision | Reason | Affects A? |
|---|---|---|---|---|
| 1 | 2026-08-10 | Battery whitelist flow integrated as a phase inside `OnboardingScreen.kt` (`BatteryWhitelistPhase`) rather than a separate standalone screen | Eliminates a navigation state and keeps the onboarding as a single linear flow. The standalone `BatteryWhitelistScreen.kt` still exists as dead code from Person A's PR — can be deleted. | No |
| 2 | 2026-08-16 | All screen files placed flat under `ui/screens/` instead of subdirectories (`ui/home/`, `ui/leaderboard/`, etc.) | Simpler to manage for a two-person team; subdirectories add overhead without meaningful organization benefit at this project's scale. | No |
| 3 | 2026-08-16 | Service Health merged into `SettingsScreen.kt` rather than a separate `ServiceHealthScreen.kt` | Settings screen already shows service health status as its top section. A separate screen would duplicate the same information. | No — but B's `SettingsScreen` defines its own `ServiceHealthState` enum that shadows A's Room entity. Must be reconciled when wiring real data. |
| 4 | 2026-08-16 | `ScrollaFormatters` (presentation-only) created in `ui/screens/` as a stopgap for `DistanceFormatter` from `model/` | `model/DistanceFormatter.kt` only has `pxToCm()`, not `cmToKm()` or display formatting. `ScrollaFormatters.formatDistance()` handles presentation formatting. When A adds `cmToKm()` to the shared model, B's ViewModels will call that for data transformation, and `ScrollaFormatters` stays for presentation. | Yes — A needs to add `cmToKm()` to `model/DistanceFormatter.kt` per DATA_CONTRACT §5 |
| 5 | 2026-08-16 | `isFirstLaunch` persisted in SharedPreferences (`scrolla_prefs`) | So returning signed-in users go Splash → Home directly, skipping onboarding. The flag is flipped when `onFinishOnboarding` fires. | No |

---

## 10. KNOWN ISSUES & ACTIVE BUGS

| # | Discovered | Description | Severity | Sprint | Resolved? |
|---|---|---|---|---|---|
| 1 | 2026-08-16 | `BatteryWhitelistScreen.kt` is dead code — unreferenced anywhere after `OnboardingScreen` refactor. Should be deleted. | 🟢 Low | Cleanup | ☐ |
| 2 | 2026-08-16 | `SettingsScreen.kt` defines its own `ServiceHealthState` enum (`ACTIVE`, `STOPPED`, `DEGRADED`, `INTERRUPTED`) which shadows A's Room entity `com.scrolla.room.ServiceHealthState`. When wiring real data, must reconcile or map between them. | 🟡 High | S2 | ☐ |
| 3 | 2026-08-16 | `SignInActivity.kt` in `ui/auth/` is a legacy activity-based sign-in flow. The current app uses `SignInScreen.kt` (Compose) instead. `SignInActivity` may be dead code — verify before deleting. | 🟢 Low | Cleanup | ☐ |
| 4 | 2026-08-16 | `ScrollaFormatters.kt` has a remaining encoding artifact: line 13 shows `â‰¥` instead of `≥` and line 27 shows `Â§` instead of `§`. | 🟢 Low | Cleanup | ☐ |
| 5 | 2026-08-16 | `GroupRepository`, `SyncManager`, and `LeaderboardRepository` missing. | 🔴 Critical | S1-S2 | ☐ |
| 6 | 2026-08-16 | `GoogleSignInOptions` API used in `SignInScreen.kt` and `SignInActivity.kt` is deprecated by Google. Should migrate to Credential Manager API before v1 release. | 🟡 High | S3 | ☐ |

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
- `ui/screens/SettingsScreen.kt` — this screen depends on `observeServiceHealth()` Flow; changes to the Flow's emitted type require B's knowledge

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

**2026-08-10**
- Built out full Compose `OnboardingScreen.kt` flow with `OnboardingPhase` enum (Welcome -> Reveal -> Invitation -> Permission -> Battery Whitelist -> Join Group).
- Implemented ambient background glow and spring transitions based on brand guidelines.
- Wired `onGrantPermission` to `ACTION_ACCESSIBILITY_SETTINGS` intent in `MainActivity.kt`.
- Updated `BatteryWhitelistHelper.kt` instructions to use generic "Scrolla" naming and removed list number prefixes.
- Cleaned up `MainActivity.kt` navigation flow.
- Checked off S1.B3 in SPRINT_LOG.md.

**2026-08-16**
- Integrated all 11 new UI Lab screens + `DesignSystem.kt` into main Scrolla project (branch `b/integrate-ui-lab-screens`, PR #4 merged).
- Wired `MainShell` as the home destination in `MainActivity.kt` — full 4-tab bottom navigation now functional.
- Restored 6 missing dependency declarations in `libs.versions.toml` that were accidentally dropped during the onboarding-ui merge (PR #3, commit `019f746`).
- Fixed text encoding artifacts (`â€"` → `—`, `â†'` → `→`, `Â·` → `·`, `â"€` → `─`) across 13 screen files — caused by UTF-8 → Windows-1252 mojibake during UI Lab copy.
- Added `isFirstLaunch` persistence via SharedPreferences so returning users skip onboarding: Splash → Home directly.
- Confirmed `BatteryWhitelistScreen.kt` is now dead code (battery whitelist is handled by `OnboardingScreen`'s `BatteryWhitelistPhase`).
- **Next session priorities:**
  1. Write Firestore security rules (S1.B5) — ✅ Done.
  2. Build `GroupRepository.kt` for create/join flows (S1.B8, S1.B9) — ✅ Done.
  3. Wire UI flows to `GroupRepository`.
  4. Delete dead code: `BatteryWhitelistScreen.kt`, possibly `SignInActivity.kt`.
  4. Fix remaining encoding artifacts in `ScrollaFormatters.kt`.
  5. Reconcile `SettingsScreen.ServiceHealthState` enum with Room entity before wiring real data.

---

*This file is owned by Person B. A reads Section 1 to know what B is waiting on. AI agents read it to avoid re-litigating implementation decisions already made.*
