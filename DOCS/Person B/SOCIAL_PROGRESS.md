# SOCIAL_PROGRESS.md — Person B (Social & Experience Track)
**Owner:** Person B
**Track:** `ui/`, `firestore/`, `auth/`, `leaderboard/`, `gamification/`
**Last updated:** 2026-09-04
**AI agents reading this:** This is Person B's working file. Before suggesting any implementation in B's folders, read the current status, known issues, and dependency sections. Never suggest wiring a Compose screen to real sensor data until Section 1's handoff status shows ✅. Cross-reference `DATA_CONTRACT.md` Section 4 for every function B calls from `ScrollRepository`. Never write to Firestore daily totals directly — only `triggerFirestoreSync()` does that (A's function, B calls it on a timer).

---

## 1. CURRENT STATUS — ONE-LINER FOR PERSON A

> _(B updates this line at the end of every session so A knows where things stand without reading the whole file)_

**Sprint 1 (B's sub-track):** 🟢 9 of 10 — only **S1.B4** (phone linking) remains. Still zero `linkWithCredential` calls in the source.
**Sprint 2:** ✅ **B's track is complete.** S2.1–S2.5, S2.8 and S2.9 checked off. No screen renders mock data. Remaining Sprint 2 work is A's: S2.6 (widget, untouched). S2.7 is now fully wired on B's side and waits only on a device pass.
**Sprint 3:** 🟡 In progress — S3.1 and S3.6 done; S3.2/S3.3/S3.5/S3.7 partial; S3.4 now written but unverified; S3.8–S3.15 untouched.

> ### ⚠️ Two things gate everything below, and neither is code
>
> **1. Fourteen commits sit unmerged on `b/health-card-cleanup`** (37 files, +2,525/-210 against `origin/main`). The branch is
> pushed; no PR is open. `main` does not contain account deletion, the group
> record write, data export, rename/leave group, the snackbar bus, haptics, the
> sub-metre fix, or four of the five test files. Everything in this file that is
> newer than 2026-08-25 describes that branch, not `main`.
>
> **2. The Firestore rules are reviewed and not deployed.** `isSelfLeave()` was
> approved by A on 2026-08-25 (REVIEW_LOG #5) and `isGroupRename()` self-approved
> on 2026-08-26 (#6). Until they are published, **delete account, leave group and
> rename group all fail at the server on a real device** — the code is merged-ready
> and non-functional. This is the cheapest unblock in the project.

**Waiting on A for:**
- **S2.6 — the widget.** Nothing exists in `app/src` (no `AppWidgetProvider`, no Glance). B has shipped `setPrimaryGroup()` *and* wired a UI caller for it (`GroupSwitcherScreen` → "set widget group"), so this is now a fully built producer with no consumer at all.
- **P0.3 — Crashlytics.** A took this on 2026-08-25 after the Xiaomi crash proved unrecoverable. Nothing has landed yet.
- **P2.4e — split `degradedReason`** into flush and sync columns. Three writers now share one field, so a scroll flush clears a sync error within ten seconds.
- **Retro-review of REVIEW_LOG #6 and #7**, both self-signed by B because A was unavailable. §2 exists because reviews #2 and #4 each caught a real bug this way.
- **A's own rows in `DEVICE_TEST_LOG.md`** — A's device is still `_to fill_` in the friend-group table, and the Section 1 release gate counts manufacturers.

**Resolved since 2026-08-24:**
- **P2.4 is done, by A.** `isServiceRunning` is now set in `onServiceConnected()` and cleared in `onDestroy()`/`onUnbind()`, `lastEventTimestamp` is finally written, and the master accessibility switch is consulted. B's UNKNOWN guard was removed as obsolete. Confirmed by B on the Xiaomi the crash happened on.
- **P2.6 is no longer an ownership question — B took it end to end** (2026-08-25). `SyncViewModel.updateGroupRecords()` triggers it and `GroupRepository.updateGroupRecordIfBetter()` writes it, both in B's half, so no edit-together seam was needed. `RecordEligibility` fails closed on today, future days, zero-distance days, never-written timestamps, corrupt rows, and days whose last scroll was before 18:00 — because the record is a *minimum*, so every failure mode of the tracker produces a winning score. See Section 10, issue 7.

**What B has built:** all 15 screens wired to real data through ViewModels, with `ui/ScrollaGraph.kt` as the composition root. Firestore rules written, A-reviewed and deployed (bar the two above). Group create/join/switch/rename/leave flows. Account deletion and CSV export. The multi-user pipeline verified end to end on 2026-08-24 with a second account on a second device. **54 unit tests** across five files.

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
| 8 | Profile | `ui/screens/ProfileScreen.kt` | 🟢 Code complete | ☑ `ProfileViewModel` | N/A | ☑ groups read + retry (2026-08-24) | ☐ |

### Layer 3 — Detail Screens (Screens 9–15)

| # | Screen | File | Status | Real data? | Empty state? | Error state? | Device checked? |
|---|---|---|---|---|---|---|---|
| 9 | Settings + Service Health | `ui/screens/SettingsScreen.kt` | 🟢 Code complete | ☑ `SettingsViewModel` collects `observeServiceHealth()`; last-recorded **and** last-sync times both rendered | N/A | ☑ INACTIVE / INTERRUPTED / UNKNOWN states | ◐ health card only (Xiaomi, 2026-08-25) |
| 10 | Group switcher list | `ui/screens/GroupSwitcherScreen.kt` | ✅ Verified on device | ☑ `LeaderboardViewModel` | ☑ no groups yet | ☑ via snackbar + retry | ☑ 2 groups, 2026-08-24 |
| 11 | Join group | `ui/screens/JoinGroupScreen.kt` | ✅ Verified on device | ☑ `GroupViewModel` | N/A | ☑ code not found | ☑ 2nd account joined, 2026-08-24 |
| — | Create group | `ui/screens/CreateGroupScreen.kt` | ✅ Verified on device | ☑ `GroupViewModel` | N/A | ☑ | ☑ 2026-08-24 |
| 12 | Weekly recap card | `ui/screens/WeeklyRecapScreen.kt` | 🟡 Data wired, milestone not built | ☑ `WeeklyRecapViewModel` | ☑ added 2026-08-26 — it had none, and rendered a confident "0 m" for an empty week | ☐ | ☐ |
| 13 | Personal records | `ui/screens/PersonalRecordsScreen.kt` | 🟢 Code complete | ☑ `PersonalRecordsViewModel` | ☑ best-week card hidden below 7 consecutive days | ☐ (Room-backed) | ☐ |
| 14 | Group hall of fame | `ui/screens/HallOfFameScreen.kt` | 🟢 Code complete | ☑ `HallOfFameViewModel` — and something finally **writes** the record as of 2026-08-25 | ☑ record not set yet | ☑ error + retry | ☐ |
| 15 | App breakdown detail | `ui/screens/AppBreakdownScreen.kt` | 🟢 Code complete | ☑ `AppBreakdownViewModel` | ☑ no app data | ☐ (Room-backed) | ☐ |

**Status key:** 🔴 Not started · 🟡 In progress / UI shell · 🟢 Code complete · ✅ Verified on device

> **The whole of Layer 3 was marked "🟡 UI shell / mock data" until 2026-09-04, and had been wrong since 2026-08-23.** Every one of these screens has been reading real data through a ViewModel since PR #6. Corrected during the pre-PR doc pass. Five of them (Settings, Weekly Recap, Personal Records, Hall of Fame, App Breakdown) are still **compile-verified only** — that is PREMIUM_CHECKLIST P2.2 and it remains open.

> **Note on file paths:** The original plan had screens in subdirectories (`ui/home/`, `ui/leaderboard/`, etc.) but the actual implementation puts all screens flat under `ui/screens/`. This is the current reality. Service Health is integrated into `SettingsScreen.kt` rather than being a separate `ServiceHealthScreen.kt`.

---

## 3. COMPONENT STATUS — NON-SCREEN WORK

| Component | File(s) | Status | Verified? | Notes |
|---|---|---|---|---|
| Firebase project config | `google-services.json` + `build.gradle.kts` | 🟢 Complete | ☑ | Firebase Auth + Firestore deps configured, SHA-1 fingerprint added |
| Firebase Auth — Google sign-in | `auth/AuthRepository.kt` + `ui/auth/SignInActivity.kt` + `ui/screens/SignInScreen.kt` | 🟢 Complete | ☑ | Google Sign-In flow implemented with Firebase credential exchange. Dual path: `SignInActivity` (legacy activity-based) and `SignInScreen` (Compose-based, used in current flow) |
| Firebase Auth — phone linking | `auth/AuthRepository.kt` | 🔴 Not started | ☐ | Test: same UID before and after linking |
| Firestore security rules | `firestore/firestore.rules` | 🟢 Complete | ☑ | Deployed to Firebase Console and verified in Rules Playground. M2 Review logged. |
| Group create flow | `firestore/GroupRepository.kt` + `GroupViewModel` | ✅ Verified | ☑ | Wired to `CreateGroupScreen`. The join code **is** the group document id. |
| Group join flow | `firestore/GroupRepository.kt` + `GroupViewModel` | ✅ Verified | ☑ | Second account joined a real group 2026-08-24 |
| Group membership list | `GroupSwitcherScreen.kt` + `LeaderboardViewModel` | ✅ Verified | ☑ | Real memberships; switching re-fetches (S2.5) |
| Group rename / leave | `firestore/GroupRepository.kt` | 🟢 Code complete | ☐ | Built 2026-08-26. **Both fail on device until the rules are deployed.** Leaving also clears that group's `dailyTotals` — removing only the `members` entry would leave a live number on the board. |
| Firestore sync timer | `ui/screens/SyncViewModel.kt` | 🟢 Complete | ☑ | 15-min constant + `RefreshOnResume` on ON_RESUME |
| Leaderboard polling | `LeaderboardViewModel.refreshIfStale()` | ✅ Verified | ☑ | One-shot `get()`, staleness-gated. Zero `addSnapshotListener` in the tree. |
| Pull-to-refresh | `LeaderboardScreen.kt` | 🟢 Complete | ☐ | Deliberately bypasses `LEADERBOARD_CACHE_STALE_MS` — the cache exists to stop incidental reads, not to overrule an explicit ask |
| Multi-group write | A's `triggerFirestoreSync()` | ✅ Verified | ☑ | Same `userId_date` doc under every group; confirmed 2026-08-24 |
| Hall of fame update | `SyncViewModel.updateGroupRecords()` + `GroupRepository.updateGroupRecordIfBetter()` | 🟢 Code complete | ☐ | Written 2026-08-25, closing the gap found the day before. Strictly-lower, not `<=`, so an equal value never takes the record from whoever set it first. **Never seen on a device** — P2.6d. |
| Record eligibility gate | `firestore/RecordEligibility.kt` | 🟢 Complete | ☑ | 12 tests. Fails closed on every rule, because a broken tracker and a quiet day are the same shape in the data — and the record is a minimum. |
| Most improved calculation | — | 🔴 Not started | ☐ | Week-over-week delta. Banner UI exists; `MainShell` passes `null`. |
| Most consistent calculation | — | 🔴 Not started | ☐ | Lowest variance over 7 days. `LEADERBOARD_MOST_CONSISTENT` is written copy for a feature that does not exist. |
| Recap card image generation | — | 🔴 Not started | ☐ | **This is the S3.5 milestone and it is not built.** `onShareClick` now shares the figure as text — honest, and not a Bitmap card. |
| Account deletion flow | `auth/AuthRepository.kt` + `GroupRepository.deleteAllUserData()` | 🟢 Code complete | ☐ | Built 2026-08-25. Order is load-bearing: cloud → Room → credential, because every rule is gated on `request.auth.uid`. Aborts if any group fails to clear. **Fails at `isSelfLeave` until the rules deploy.** |
| Local data export | `ui/screens/DataExport.kt` | 🟢 Complete | ☑ | CSV via FileProvider + `ACTION_SEND`. 10 tests. Sits directly above Delete. |
| Snackbar / transient errors | `ui/ScrollaMessages.kt` | 🟢 Complete | ☐ | Singleton `SharedFlow` bus; host sits above `AnimatedContent` so messages from Settings and the group flows can appear |
| Haptics | `ui/components/ScrollaHaptics.kt` | 🟢 Complete | ☑ | Driven from `VibratorManager`, not `performHapticFeedback`, plus a Settings toggle |
| ScrollaFormatters (presentation) | `ui/screens/ScrollaFormatters.kt` | 🟢 Complete | ☑ | `formatDistance()` and `formatOrdinal()` — presentation-only, used across screens |
| ScrollaStrings (UI copy) | `ui/screens/ScrollaStrings.kt` | 🟢 Complete | ☑ | Centralised copy for every screen. **214 strings, 43 of them never rendered** (re-counted 2026-08-26). That audit has now paid off three times — it found the delete-flow copy, the type-to-confirm hint, and the "[deleted]" record promise nothing was keeping. |
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
| Home | Dash + "waiting for the first scroll", never a confident 0.0 | ☑ | — (Room-backed; `ScrollRepository` returns safe defaults and never throws) | N/A |
| Leaderboard | Personal stats when group size = 1 | ☑ | "Couldn't load — tap to retry" | ☑ with retry |
| Leaderboard | "[left]" label for departed members | ☐ | — | — |
| Insights | "Start scrolling to see your stats" | ☑ | — (Room-backed) | N/A |
| Group switcher | "You're not in any groups yet" | ☑ | — | ☐ |
| Join group | — | — | "Group not found — check the code" | ☑ |
| Personal records | Best-week card hidden below 7 consecutive days; 4 empty states counted | ☑ | — (Room-backed) | N/A |
| Hall of fame | "No record set yet — you could be first" | ☑ | "Couldn't load your groups — tap to retry" | ☑ with retry (2026-08-24) |
| Recap card | Dash + different copy for a week with nothing recorded | ☑ (2026-08-26) | — | ☐ |
| App breakdown | "No app data yet today"; sub-metre values render `<1 m`, not `0 m` | ☑ | — (Room-backed) | N/A |
| Settings / Service Health | — | — | INACTIVE / INTERRUPTED / UNKNOWN, with last-recorded and last-sync times | ☑ |
| Sign in | — | — | "Sign-in failed — try again" | ☑ (onSignInError callback wired) |
| Profile — groups row | — | — | "Couldn't load your groups — tap to retry" | ☑ (2026-08-24) |
| Profile — delete account | — | — | `deleteError` on every failure path, and deletion **aborts** rather than dropping the credential when a group won't clear | ☑ (2026-08-25) |
| Transient failures (any screen) | — | — | Snackbar bus with a working Retry | ☑ (2026-08-25) |

> **Rule:** A blank white screen or a crash is never an acceptable empty or error state.
>
> **Corollary added 2026-08-24, learned the hard way:** an empty state shown *because a read failed* is worse than a blank screen, not better. `HallOfFameViewModel` and `ProfileViewModel` both read Firestore through `getOrNull()`, which discards the error — so a dropped connection rendered as "no record set yet — you could be first" and "You're not in any groups yet". The second was told to users who were in two groups. Those are confident false statements, which is the failure mode this rule exists to prevent, arriving through the door the rule left open. **Never call a Firestore read with `getOrNull()` behind an empty state.** If you're unsure what the empty state should say, check `UI_COPY.md` before inventing copy inline.

---

## 6. GAMIFICATION FEATURE STATUS

| Feature | Screen | Status | Data source | Notes |
|---|---|---|---|---|
| Reverse leaderboard (lowest wins) | Screen 6 | ✅ Verified | `getGroupLeaderboard()` | Ascending confirmed on screen with two differing values, 2026-08-24 |
| Landmark comparison | Screen 5 | 🟢 Complete | `DistanceFormatter.nearestLandmark()` | Gated to 0.5–2.0× in `HomeViewModel` — the contract function returns the nearest landmark however absurd, so 10 cm would otherwise read "Eiffel Tower" |
| Most improved highlight | Screen 6 | 🔴 Not started | Firestore — week-over-week delta | Banner UI exists; `MainShell:522` passes `null`. **`LeaderboardScreen` still defaults it to `"Lewis"`** — see Section 10, issue 9. |
| Most consistent recognition | Screen 6 | 🔴 Not started | Firestore — 7-day variance | Lowest variance wins. Copy written (`LEADERBOARD_MOST_CONSISTENT`), feature absent. |
| Personal records | Screen 13 | 🟢 Complete | `getPersonalBestDay()` | Real records. Best-week window walks **consecutive calendar dates**, not consecutive rows — a day with no scrolling has no row, so a row-based window would report a week that never happened. "First time under a self-set threshold" is still unbuilt: there is no threshold-setting UI anywhere. |
| Time-of-day insight framing | Screen 5 rotating | 🟢 Complete | `getTodayPeakHour()` | Card hides entirely when there is no peak hour yet |
| App-comparison nudge | Screen 15 | 🔴 Not started | `getTodayTopApps()` from Room | "cutting [app] by 20% would put you in 1st" needs the group board; `targetRank` is null and the line hides |
| Group hall of fame | Screen 14 | 🟢 Code complete | `updateGroupRecordIfBetter()` | **Unblocked 2026-08-25** — the write that never existed now exists, gated by `RecordEligibility`. Never confirmed on a device (P2.6d). |
| Weekly recap shareable card | Screen 12 | 🟡 Data only | `WeeklyRecapViewModel` | Real 7-day total, proximity-gated landmark, and an empty state as of 2026-08-26. **No Bitmap** — the share is plain text, and the Bitmap is the milestone. |
| Home rotating insight | Screen 5 | 🟢 Complete | `HomeInsights.select()` | Done 2026-08-26. Three real types plus the placeholder, 8 tests. |

**Rotation logic for the Home screen insight card — as built, 2026-08-26.**
The original plan was a priority order (first type with data wins). What shipped
is a **candidate pool**, because a strict priority order means the lower types
are only ever seen when the higher ones fail, which is not rotation. `HomeInsights.select()`
collects every insight that genuinely qualifies, then picks among them on
`dayOfYear` so the card is stable for a whole day — one that changes on every
recomposition reads as a bug.

The three real types, each gated so it cannot invent:
1. **Peak hour** — whenever `getTodayPeakHour()` is non-null.
2. **Personal best** — only once today is genuinely *under* the best day. "You're close to your best" while sitting above it is flattery, not information.
3. **Quick win** — only when today is below yesterday, and no comparison at all when there is no row for yesterday.
4. **Placeholder** when none qualify — which is what the never-rendered placeholder copy was written for.

> The app-comparison nudge is not in the pool: it needs the group leaderboard, which is Screen 15's blocker too.

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
| 7 | 2026-08-24 | **Nothing anywhere in `app/src/main` writes `recordKm`, `recordHolder` or `recordDate`.** Every occurrence is a read, a data-class field, a ViewModel mapping or a Compose preview default. So S3.4 renders its empty state permanently, `LeaderboardViewModel.groupBestDay` is always null, and `isRecordImprovement()` — written, A-reviewed and Playground-verified in both directions — guards a write no client makes. The sprint log called S3.4 "double-blocked" on sync and rules; both are now fixed and it still would not work. Tracked as PREMIUM_CHECKLIST P2.6. | 🔴 Critical | S3 | ☑ **Written 2026-08-25.** B owns it end to end — no edit-together seam was needed, since `SyncViewModel` (`ui/`) already drives the cadence and `GroupRepository` (`firestore/`) already owns group writes. `RecordEligibility` fails closed on today, future days, zero-distance days, never-written timestamps, corrupt rows, and days whose last scroll was before 18:00. **Not yet confirmed on a device — P2.6d.** |
| 8 | 2026-08-24 | `HomeScreen` declares `rankPosition: Int? = 2` and `HallOfFameScreen` declares `recordHolderName = "Lewis"` / `recordDate = "July 12"` as **default parameter values**. Preview scaffolding, but one careless call site away from rendering a fabricated number in an app whose stated discipline is never showing a plausible fake one. Tracked as PREMIUM_CHECKLIST P4.2. | 🟡 High | S3 | ☑ 2026-08-26 — and it was worse than logged: `ProfileScreen` carried a whole fake profile (name, best day, seven-day average, group count, group name). All now null or zero. |
| 9 | 2026-09-04 | **The P4.2a sweep missed `LeaderboardScreen`.** It still declares `groupName = "College Friends"`, `mostImprovedName: String? = "Lewis"` and a three-row fake `entries` list as default parameter values (`LeaderboardScreen.kt:75-80`). `MainShell:522` passes `null`/real data, so nothing fabricated reaches a user today — which is exactly what was true of `rankPosition = 2` before it was fixed. Same landmine, same screen family, found by grepping rather than by the sweep that was supposed to catch it. Reopens PREMIUM_CHECKLIST P4.2a. | 🟡 High | S3 | ☐ |
| 10 | 2026-09-04 | **Fourteen commits (37 files) are unmerged on `b/health-card-cleanup` and the reviewed Firestore rules are undeployed.** Delete account, leave group and rename group are finished code that fails at the server on a real device. Not a bug in the usual sense — a delivery gap — but it has the same effect as a bug for anyone holding the APK. | 🔴 Critical | S3 | ☐ |

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

**2026-09-04 — doc pass before opening the PR**

All four of those priorities landed between 2026-08-25 and 2026-08-26, along with
export, rename/leave, the snackbar bus, haptics, pull-to-refresh, the sub-metre
fix and the insight rotation. **None of it is in `main`.**

This file had drifted badly in both directions and was corrected against the
source today, not from memory:

- **Section 2 understated the work.** All seven Layer 3 screens were still marked
  "🟡 UI shell / mock data"; every one has read real data through a ViewModel
  since PR #6 on 2026-08-23. Twelve days of wrong status.
- **Section 3 understated it further** — nine components marked 🔴 Not started
  that are built and, in four cases, device-verified.
- **Section 6 overstated one thing**: Hall of Fame was "🔴 Blocked", which stopped
  being true on 2026-08-25.
- **Section 10 issue 7 was still open** on paper and closed in code.

Worth naming the pattern, because it is the third time: *the docs are only
trustworthy in the direction someone last had a reason to look.* We caught
"nothing writes `recordKm`" by grepping for a write, and the fake-default sweep
by grepping for defaults — both times the prose looked fine. Same again today,
which is how issue 9 turned up: **the P4.2a sweep missed `LeaderboardScreen`**,
which still defaults to a fake group name, a fake "most improved" holder and
three invented leaderboard rows.

- **Next, in order:**
  1. **Open the PR** — 14 commits across 37 files, and everything else is downstream of it.
  2. **Deploy the rules.** Delete, leave and rename are dead on device until then, and #5 is already A-approved.
  3. **Device pass**: P2.6d (record reaching Hall of Fame), P2.7e (rename/leave), P2.2a/b (the five never-run screens, with data and without), and delete end to end.
  4. **P4.1** — settle the 2.8 km onboarding figure before another APK goes out.
  5. Issue 9, then P0.5d (privacy screen), then S3.5's Bitmap.

---

*This file is owned by Person B. A reads Section 1 to know what B is waiting on. AI agents read it to avoid re-litigating implementation decisions already made.*
