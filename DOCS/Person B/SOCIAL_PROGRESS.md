# SOCIAL_PROGRESS.md — Person B (Social & Experience Track)
**Owner:** Person B
**Track:** `ui/`, `firestore/`, `auth/`, `leaderboard/`, `gamification/`
**Last updated:** 2026-08-24
**AI agents reading this:** This is Person B's working file. Before suggesting any implementation in B's folders, read the current status, known issues, and dependency sections. Never suggest wiring a Compose screen to real sensor data until Section 1's handoff status shows ✅. Cross-reference `DATA_CONTRACT.md` Section 4 for every function B calls from `ScrollRepository`. Never write to Firestore daily totals directly — only `triggerFirestoreSync()` does that (A's function, B calls it on a timer).

---

## 1. CURRENT STATUS — ONE-LINER FOR PERSON A

> _(B updates this line at the end of every session so A knows where things stand without reading the whole file)_

**Sprint 1 (B's sub-track):** 🟢 9 of 10 — only **S1.B4** (phone linking) remains, deferred to Sprint 3.
**Sprint 2:** ✅ **B's track is complete.** S2.1, S2.2, S2.3, S2.4, S2.5 and S2.9 all checked off. No screen renders mock data. Remaining Sprint 2 work is A's: S2.6 (widget, untouched).
**Sprint 3:** 🟡 In progress — S3.1 done; S3.2/S3.3/S3.6/S3.7 partial; S3.4 blocked (see below); S3.5 barely started.

**Waiting on A for:**
- **S2.6 — the widget.** Nothing exists in `app/src` (no `AppWidgetProvider`, no Glance). B has already shipped `setPrimaryGroup()` specifically so the widget can read the primary group, so this is a built producer with no consumer.
- **P2.4 — `isServiceRunning` is inferred, not observed.** `onServiceConnected()` does not set it; only a successful `flushBatch()` does, so a freshly enabled service reads as not running. B added a UI-side guard (a never-flushed row maps to UNKNOWN rather than INTERRUPTED), but the accurate fix is in A's service.
- **P2.6 — an ownership decision on the group record.** See Section 10, issue 7.

**Resolved since 2026-08-16:** `DistanceFormatter.cmToKm()` landed (S1.B10) and both tracks now use it. `triggerFirestoreSync()` is implemented, merged, and **proven on two devices**.

**What B has built:** all 15 screens wired to real data through ViewModels, with `ui/ScrollaGraph.kt` as the composition root. Firestore rules written, A-reviewed and deployed. Group create/join/switch flows working with real groups. The multi-user pipeline verified end to end on 2026-08-24 with a second account on a second device.

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
| 5 | Home (default tab) | `ui/screens/HomeScreen.kt` | ✅ Verified on device | ☑ `HomeViewModel` + group rank via `selfStanding()` | ☑ dash, not 0.0, before any data | ☐ sync error | ☑ |
| 6 | Leaderboard | `ui/screens/LeaderboardScreen.kt` | ✅ Verified on device | ☑ `LeaderboardViewModel` | ☑ no-group and no-totals states are distinct | ☑ error + retry | ☑ 2 accounts, 2026-08-24 |
| 7 | Insights | `ui/screens/InsightsScreen.kt` | 🟢 Code complete | ☑ `InsightsViewModel` | ☑ | ☐ | ☑ |
| 8 | Profile | `ui/screens/ProfileScreen.kt` | 🟢 Code complete | ☑ `ProfileViewModel` | N/A | ☐ delete failed | ☐ |

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
| 1 | 2026-08-24 | S2.T1 basic sync, two accounts | A `dailyTotals` doc per user with today's date and non-zero `totalKm` | Leaderboard read "2 of 2 synced today" with a real row each | ✅ | First multi-user sync in the project's history |
| 2 | 2026-08-24 | S2.T3 multi-group write | Same `userId_date` doc under both groups | One account in two groups (2 members / 1 member); switching re-fetches correctly | ✅ | Closes S2.5 |
| 3 | 2026-08-24 | S2.T4 no `onSnapshot()` | Zero matches for `addSnapshotListener` | Zero matches | ✅ | Re-run this grep before every PR |

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
| Reverse leaderboard (lowest wins) | Screen 6 | ✅ Verified | `getGroupLeaderboard()` | Ascending confirmed on screen with two differing values, 2026-08-24 |
| Landmark comparison | Screen 5 | 🟢 Complete | `DistanceFormatter.nearestLandmark()` | Gated to 0.5–2.0× in `HomeViewModel` — the contract function returns the nearest landmark however absurd, so 10 cm would otherwise read "Eiffel Tower" |
| Most improved highlight | Screen 6 | 🟡 UI shell | Mock string | Banner UI exists with placeholder, not wired to Firestore |
| Most consistent recognition | Screen 6 | 🔴 Not started | Firestore — 7-day variance | Lowest variance wins |
| Personal records | Screen 13 | 🟡 UI shell | Mock data | `PersonalRecordsScreen.kt` exists with mock records |
| Time-of-day insight framing | Screen 5 rotating | 🟢 Complete | `getTodayPeakHour()` | Card hides entirely when there is no peak hour yet |
| App-comparison nudge | Screen 15 | 🔴 Not started | `getTodayTopApps()` from Room | "cutting [app] by 20% would put you in 1st" — never synced |
| Group hall of fame | Screen 14 | 🔴 Blocked | — | Reads `recordKm` correctly, but **nothing writes it** — see Section 10, issue 7 |
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

- [x] Documents sorted **ascending** by `totalKm` — lowest km = Rank 1 (winner). A descending sort is the first mistake an AI agent will make since most leaderboards sort descending. *Verified on screen 2026-08-24 with two differing values, not only by inspection.*
- [ ] "You" row is visually distinct — highlighted accent colour, not just rank number.
- [ ] Rank 1 row uses a success/green highlight — the winner has the *smallest* number, which looks counterintuitive and needs visual reinforcement.
- [ ] Numbers show `totalKm` only — no per-app breakdown, no `topApp` field, no insight about *why* a user's number is high. Per `DATA_CONTRACT.md` Section 3.2 and `scrolla_project_summary.md` Section 5 (privacy fix).
- [x] A group of exactly 1 member shows personal stats, not "Rank 1 of 1" (that would make the reverse mechanic look broken). *Enforced in two places: the Leaderboard screen, and `selfStanding()` which returns null below 2 synced members so Home's standing card shows a dash. Unit-tested.*
- [ ] A departed member shows "[left]" next to their name in historical views, not their actual display name.
- [x] Group switcher correctly re-fetches the leaderboard for the newly selected group, not cached data from the previous group. *Verified 2026-08-24 with one account in two groups (2 members and 1 member). `selectGroup()` clears `lastLoadedGroupId` before refreshing.*
- [ ] Most improved banner is visually separate from the ranked list — it's a different kind of recognition, not "rank 0."
- [x] Leaderboard data refreshes on tab open (with staleness check). *Home reads the same activity-scoped `LeaderboardViewModel` rather than fetching its own copy, so showing rank on Home costs no additional Firestore reads and the two screens cannot disagree.* Still open: refreshing when `triggerFirestoreSync()` completes.

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
| 1 | 2026-08-16 | `BatteryWhitelistScreen.kt` is dead code — unreferenced anywhere after `OnboardingScreen` refactor. Should be deleted. | 🟢 Low | Cleanup | ☑ |
| 2 | 2026-08-16 | `SettingsScreen.kt` defines its own `ServiceHealthState` enum (`ACTIVE`, `STOPPED`, `DEGRADED`, `INTERRUPTED`) which shadows A's Room entity `com.scrolla.room.ServiceHealthState`. When wiring real data, must reconcile or map between them. | 🟡 High | S2 | ☑ |
| 6 | 2026-08-16 | `GoogleSignInOptions` API used in `SignInScreen.kt` is deprecated by Google. Should migrate to Credential Manager API before v1 release. | 🟡 High | S3 | ☐ |
| 7 | 2026-08-24 | **Nothing anywhere in `app/src/main` writes `recordKm`, `recordHolder` or `recordDate`.** Every occurrence is a read, a data-class field, a ViewModel mapping or a Compose preview default. So S3.4 renders its empty state permanently, `LeaderboardViewModel.groupBestDay` is always null, and `isRecordImprovement()` — written, A-reviewed and Playground-verified in both directions — guards a write no client makes. The sprint log called S3.4 "double-blocked" on sync and rules; both are now fixed and it still would not work. Needs an ownership decision first: the natural trigger is after a successful sync in A's `ScrollRepositoryImpl`, but the group-document write is B's per §2. Also needs care that only a **finished** day sets a record — lowest wins, so a running total written mid-morning would break the record daily. Tracked as PREMIUM_CHECKLIST P2.6. | 🔴 Critical | S3 | ☐ |
| 8 | 2026-08-24 | `HomeScreen` declares `rankPosition: Int? = 2` and `HallOfFameScreen` declares `recordHolderName = "Lewis"` / `recordDate = "July 12"` as **default parameter values**. Preview scaffolding, but one careless call site away from rendering a fabricated number in an app whose stated discipline is never showing a plausible fake one. Tracked as PREMIUM_CHECKLIST P4.2. | 🟡 High | S3 | ☐ |

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
  3. Wire UI flows to `GroupRepository` — ✅ Done.
  4. Delete dead code: `BatteryWhitelistScreen.kt`, possibly `SignInActivity.kt` — ✅ Done.
  5. Fix remaining encoding artifacts in `ScrollaFormatters.kt` — ✅ Done.
  6. Reconcile `SettingsScreen.ServiceHealthState` enum with Room entity before wiring real data — ✅ Done.

**2026-08-24**
- **The social half of the app works.** A friend installed the APK on a second device, signed in with their own Google account, joined a group by code, and the leaderboard rendered "2 of 2 synced today". That single line proves `joinGroup()`'s `arrayUnion` commits, A's `triggerFirestoreSync()` wrote for **both** users, `getGroupLeaderboard()` read both back, and the deployed rules permitted every step across two accounts. From 2026-08-16 to 2026-08-23 that flow was silently impossible.
- Ascending order confirmed on screen with two differing values — verified as behaviour, not only as `sortedBy` by inspection.
- Home-vs-leaderboard figures matched **on both devices independently**. The second device's check is the stronger one: a different uid against the same rules, resolving "You" versus a display name through a different branch. **S2.9 closed.**
- S2.5 closed: one account in two groups (2 members / 1 member), switching re-fetches correctly.
- **Built group rank (S2.1's last clause).** `ui/screens/GroupStanding.kt` — `selfStanding()` derives the user's position from the Group tab's own activity-scoped `LeaderboardViewModel`, so Home costs no extra Firestore reads and the two screens cannot disagree. Ties share the better rank; everyone sits at 0.0 km each morning and list position would otherwise hand out an arbitrary 3rd place. The denominator is members who have **synced today**, not group size. Returns null in four cases rather than inventing a position.
- **First unit tests in the project's history.** `GroupStandingTest` — 10 tests, passing in debug and release. `selfStanding()` was deliberately written as a pure function over UI state so it needs no injection seam; the ViewModels themselves still do (PREMIUM_CHECKLIST P0.1f).
- **Found:** nothing writes the group record. See Section 10, issue 7. Worth generalising — when a screen reads a Firestore field, grep for the write before calling it code-complete.
- **Next session priorities:**
  1. Device-check the new rank card with two accounts (it is compile- and unit-verified only).
  2. P2.6 — agree ownership of the record write with A, then implement.
  3. P0.1b — `DistanceFormatterTest`, now that the test source set exists.
  4. P0.4 — account deletion, the biggest trust gap.

---

*This file is owned by Person B. A reads Section 1 to know what B is waiting on. AI agents read it to avoid re-litigating implementation decisions already made.*
